package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.HapticScapeEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous loopback TCP publisher for source-neutral local events.
 *
 * <p>Gameplay/source callback threads never perform socket I/O. Transient
 * events are best-effort and are dropped while disconnected or when the
 * bounded queue is full. State snapshots are retained and coalesced, then
 * replayed after reconnect following a source reset.</p>
 */
public final class LocalhostGameplayEventTransport implements GameplayEventSink, AutoCloseable
{
	private static final int EVENT_QUEUE_CAPACITY = 256;
	private static final long WRITER_IDLE_MILLIS = 50L;
	private static final long RECONNECT_INITIAL_MILLIS = 250L;
	private static final long RECONNECT_MAX_MILLIS = 5_000L;

	private final Object stateLock = new Object();
	private final String source;
	private final TransportWireCodec codec;
	private final Set<SourceCapability> capabilities;
	private final int port;
	private final BlockingQueue<TransportMessage.Event> eventQueue =
		new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY);
	private final Map<String, StateSnapshot> latestStates = new LinkedHashMap<>();
	private final Map<String, Long> sentStateRevisions = new LinkedHashMap<>();
	private final Thread writerThread;

	private volatile Socket socket;
	private volatile boolean connected;
	private volatile boolean closed;
	private long stateRevision;
	private long resetRevision = 1L;
	private long sentResetRevision;

	public LocalhostGameplayEventTransport(
		String source,
		TransportWireCodec codec,
		Set<SourceCapability> capabilities,
		int port)
	{
		this.source = TransportMessage.requireIdentifier(source, "source");
		this.codec = Objects.requireNonNull(codec, "codec");
		this.capabilities = TransportMessage.immutableCapabilities(capabilities);
		if (port <= 0 || port > 65_535)
		{
			throw new IllegalArgumentException("port must be between 1 and 65535");
		}
		this.port = port;
		writerThread = new Thread(this::runWriter, "HapticScape-local-event-writer");
		writerThread.setDaemon(true);
		writerThread.start();
	}

	/** Returns whether the background writer currently has a negotiated socket. */
	public boolean isConnected()
	{
		return connected;
	}

	@Override
	public void resetSourceState()
	{
		ensureOpen();
		eventQueue.clear();
		synchronized (stateLock)
		{
			resetRevision++;
			latestStates.clear();
			sentStateRevisions.clear();
		}
		wakeWriter();
	}

	@Override
	public void seedVitals(VitalsChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void seedInventory(InventoryChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void seedToxicStatus(ToxicStatusChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void onXpEvent(XpEvent event)
	{
		publish(event);
	}

	@Override
	public void onChatEvent(ChatEvent event)
	{
		publish(event);
	}

	@Override
	public void onVitalsEvent(VitalsChangedEvent event)
	{
		publishStateful(event);
	}

	@Override
	public void onInventoryEvent(InventoryChangedEvent event)
	{
		publishStateful(event);
	}

	@Override
	public void onToxicStatusEvent(ToxicStatusChangedEvent event)
	{
		publishStateful(event);
	}

	@Override
	public void onLootEvent(LootReceivedEvent event)
	{
		publish(event);
	}

	@Override
	public void onPlayerDeathEvent(PlayerDeathEvent event)
	{
		publish(event);
	}

	@Override
	public void onNotificationEvent(NotificationEvent event)
	{
		publish(event);
	}

	private void publish(HapticScapeEvent event)
	{
		ensureOpen();
		HapticScapeEvent validated = requireSource(event);
		if (!connected)
		{
			return;
		}
		eventQueue.offer(new TransportMessage.Event(validated));
	}

	private void seed(HapticScapeEvent event)
	{
		ensureOpen();
		HapticScapeEvent validated = requireSource(event);
		String key = stateKey(validated);
		synchronized (stateLock)
		{
			latestStates.put(
				key,
				new StateSnapshot(++stateRevision, new TransportMessage.State(validated))
			);
		}
		wakeWriter();
	}

	private void publishStateful(HapticScapeEvent event)
	{
		ensureOpen();
		HapticScapeEvent validated = requireSource(event);
		String key = stateKey(validated);
		boolean queued = false;
		boolean shouldWake = false;
		synchronized (stateLock)
		{
			long revision = ++stateRevision;
			latestStates.put(
				key,
				new StateSnapshot(revision, new TransportMessage.State(validated))
			);
			if (connected)
			{
				queued = eventQueue.offer(new TransportMessage.Event(validated));
				if (queued)
				{
					sentStateRevisions.put(key, revision);
				}
				else
				{
					shouldWake = true;
				}
			}
			else
			{
				shouldWake = true;
			}
		}
		if (shouldWake)
		{
			wakeWriter();
		}
	}

	private HapticScapeEvent requireSource(HapticScapeEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (!source.equals(event.getSource()))
		{
			throw new TransportProtocolException(
				"Event source does not match transport source: " + event.getSource()
			);
		}
		return event;
	}

	private static String stateKey(HapticScapeEvent event)
	{
		if (event instanceof VitalsChangedEvent)
		{
			VitalsChangedEvent vitals = (VitalsChangedEvent) event;
			return event.getType() + ":" + vitals.getKind().name();
		}
		return event.getType();
	}

	private void runWriter()
	{
		long reconnectDelayMillis = RECONNECT_INITIAL_MILLIS;
		while (!closed)
		{
			if (!connected)
			{
				if (!connect())
				{
					if (closed)
					{
						break;
					}
					waitForWork(reconnectDelayMillis);
					reconnectDelayMillis = Math.min(
						RECONNECT_MAX_MILLIS,
						reconnectDelayMillis * 2L
					);
					continue;
				}
				reconnectDelayMillis = RECONNECT_INITIAL_MILLIS;
			}

			try
			{
				PendingControl control = nextPendingControl();
				if (control != null)
				{
					write(control.message);
					markControlSent(control);
					continue;
				}

				TransportMessage.Event event = eventQueue.poll(
					WRITER_IDLE_MILLIS,
					TimeUnit.MILLISECONDS
				);
				if (event != null)
				{
					write(event);
				}
			}
			catch (InterruptedException ex)
			{
				if (closed)
				{
					Thread.currentThread().interrupt();
					break;
				}
			}
			catch (IOException | RuntimeException ex)
			{
				disconnect();
			}
		}
		disconnect();
	}

	private boolean connect()
	{
		if (closed)
		{
			return false;
		}

		Socket candidate = new Socket();
		socket = candidate;
		try
		{
			candidate.connect(
				new InetSocketAddress(LocalhostTransportEndpoint.address(), port),
				LocalhostTransportEndpoint.CONNECT_TIMEOUT_MILLIS
			);
			candidate.setTcpNoDelay(true);
			candidate.setSoTimeout(LocalhostTransportEndpoint.HANDSHAKE_TIMEOUT_MILLIS);

			TransportMessage.Hello hello = new TransportMessage.Hello(
				source,
				EventProtocol.NAME,
				EventProtocol.VERSION,
				capabilities
			);
			LocalhostFrameIo.write(candidate.getOutputStream(), codec.encode(hello));
			String responseFrame = LocalhostFrameIo.read(candidate.getInputStream());
			if (responseFrame == null)
			{
				throw new TransportProtocolException("Local event transport closed during hello");
			}
			TransportMessage response = codec.decode(responseFrame);
			if (response instanceof TransportMessage.Error)
			{
				TransportMessage.Error error = (TransportMessage.Error) response;
				throw new TransportProtocolException(
					"Transport error [" + error.getCode() + "]: " + error.getMessage()
				);
			}
			if (!(response instanceof TransportMessage.HelloAck))
			{
				throw new TransportProtocolException("Local event transport did not acknowledge hello");
			}
			TransportMessage.HelloAck ack = (TransportMessage.HelloAck) response;
			if (!EventProtocol.NAME.equals(ack.getEventProtocol())
				|| ack.getEventVersion() != EventProtocol.VERSION
				|| !capabilities.equals(ack.getCapabilities()))
			{
				throw new TransportProtocolException(
					"Local event transport acknowledged an incompatible source contract"
				);
			}

			candidate.setSoTimeout(0);
			connected = true;
			eventQueue.clear();
			synchronized (stateLock)
			{
				sentResetRevision = 0L;
				sentStateRevisions.clear();
			}
			return true;
		}
		catch (IOException | RuntimeException ex)
		{
			closeSocket(candidate);
			if (socket == candidate)
			{
				socket = null;
			}
			connected = false;
			return false;
		}
	}

	private PendingControl nextPendingControl()
	{
		synchronized (stateLock)
		{
			if (sentResetRevision < resetRevision)
			{
				return PendingControl.reset(
					resetRevision,
					new TransportMessage.Reset(source)
				);
			}

			for (Map.Entry<String, StateSnapshot> entry : latestStates.entrySet())
			{
				long sentRevision = sentStateRevisions.getOrDefault(entry.getKey(), 0L);
				StateSnapshot snapshot = entry.getValue();
				if (sentRevision < snapshot.revision)
				{
					return PendingControl.state(
						entry.getKey(),
						snapshot.revision,
						snapshot.message
					);
				}
			}
			return null;
		}
	}

	private void markControlSent(PendingControl control)
	{
		synchronized (stateLock)
		{
			if (control.stateKey == null)
			{
				sentResetRevision = Math.max(sentResetRevision, control.revision);
				return;
			}
			sentStateRevisions.put(control.stateKey, control.revision);
		}
	}

	private void write(TransportMessage message) throws IOException
	{
		Socket current = socket;
		if (!connected || current == null || current.isClosed())
		{
			throw new IOException("Local event transport socket is closed");
		}
		LocalhostFrameIo.write(current.getOutputStream(), codec.encode(message));
	}

	private void disconnect()
	{
		connected = false;
		eventQueue.clear();
		Socket current = socket;
		socket = null;
		closeSocket(current);
	}

	private static void closeSocket(Socket current)
	{
		if (current == null)
		{
			return;
		}
		try
		{
			current.close();
		}
		catch (IOException ignored)
		{
			// Best-effort shutdown.
		}
	}

	private void waitForWork(long millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException ex)
		{
			if (closed)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private void wakeWriter()
	{
		writerThread.interrupt();
	}

	private void ensureOpen()
	{
		if (closed)
		{
			throw new TransportProtocolException("Local event transport is closed");
		}
	}

	@Override
	public void close()
	{
		closed = true;
		disconnect();
		writerThread.interrupt();
		if (Thread.currentThread() != writerThread)
		{
			try
			{
				writerThread.join(2_000L);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private static final class StateSnapshot
	{
		private final long revision;
		private final TransportMessage.State message;

		private StateSnapshot(long revision, TransportMessage.State message)
		{
			this.revision = revision;
			this.message = message;
		}
	}

	private static final class PendingControl
	{
		private final String stateKey;
		private final long revision;
		private final TransportMessage message;

		private PendingControl(String stateKey, long revision, TransportMessage message)
		{
			this.stateKey = stateKey;
			this.revision = revision;
			this.message = message;
		}

		private static PendingControl reset(long revision, TransportMessage.Reset message)
		{
			return new PendingControl(null, revision, message);
		}

		private static PendingControl state(
			String stateKey,
			long revision,
			TransportMessage.State message)
		{
			return new PendingControl(stateKey, revision, message);
		}
	}
}
