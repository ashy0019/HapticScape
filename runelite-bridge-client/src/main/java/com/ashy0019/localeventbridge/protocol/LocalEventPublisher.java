package com.ashy0019.localeventbridge.protocol;

import com.ashy0019.localeventbridge.LocalEventSink;
import com.ashy0019.localeventbridge.event.ActorDeathObservation;
import com.ashy0019.localeventbridge.event.ChatMessageEvent;
import com.ashy0019.localeventbridge.event.ExperienceChangedEvent;
import com.ashy0019.localeventbridge.event.InventoryOccupancyEvent;
import com.ashy0019.localeventbridge.event.LocalEvent;
import com.ashy0019.localeventbridge.event.LootReceivedEvent;
import com.ashy0019.localeventbridge.event.NotificationEmittedEvent;
import com.ashy0019.localeventbridge.event.ResourceChangedEvent;
import com.ashy0019.localeventbridge.event.StatusChangedEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous loopback publisher for source-neutral local events.
 *
 * <p>RuneLite callback threads only update in-memory state and queues. All
 * socket connection, handshake, and writes run on one daemon executor thread.
 * Transient events are best-effort and are discarded while disconnected.
 * State observations are coalesced and replayed after reconnect.</p>
 */
public final class LocalEventPublisher implements LocalEventSink, AutoCloseable
{
	private static final int EVENT_QUEUE_CAPACITY = 256;
	private static final long RECONNECT_INITIAL_MILLIS = 250L;
	private static final long RECONNECT_MAX_MILLIS = 5_000L;

	private final Object stateLock = new Object();
	private final Object scheduleLock = new Object();
	private final String source;
	private final TransportWireCodec codec;
	private final Set<SourceCapability> capabilities;
	private final int port;
	private final BlockingQueue<QueuedEvent> eventQueue =
		new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY);
	private final Map<String, StateSnapshot> latestStates = new LinkedHashMap<>();
	private final Map<String, Long> sentStateRevisions = new LinkedHashMap<>();
	private final ScheduledExecutorService ioExecutor;
	private final AtomicBoolean ioRunning = new AtomicBoolean();

	private volatile Socket socket;
	private volatile boolean connected;
	private volatile boolean closed;
	private volatile ScheduledFuture<?> reconnectFuture;
	private long reconnectDelayMillis = RECONNECT_INITIAL_MILLIS;
	private long stateRevision;
	private long resetRevision = 1L;
	private long sentResetRevision;

	public LocalEventPublisher(
		String source,
		TransportWireCodec codec,
		Set<SourceCapability> capabilities)
	{
		this(source, codec, capabilities, LoopbackEndpoint.DEFAULT_PORT);
	}

	/** Package-private test seam; production callers always use the fixed loopback port. */
	LocalEventPublisher(
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
		this.ioExecutor = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
		requestIo();
	}

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
		requestIo();
	}

	@Override
	public void seedResource(ResourceChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void seedInventory(InventoryOccupancyEvent event)
	{
		seed(event);
	}

	@Override
	public void seedStatus(StatusChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void onExperience(ExperienceChangedEvent event)
	{
		publish(event);
	}

	@Override
	public void onChat(ChatMessageEvent event)
	{
		publish(event);
	}

	@Override
	public void onResource(ResourceChangedEvent event)
	{
		publishStateful(event);
	}

	@Override
	public void onInventory(InventoryOccupancyEvent event)
	{
		publishStateful(event);
	}

	@Override
	public void onStatus(StatusChangedEvent event)
	{
		publishStateful(event);
	}

	@Override
	public void onLoot(LootReceivedEvent event)
	{
		publish(event);
	}

	@Override
	public void onActorDeath(ActorDeathObservation event)
	{
		publish(event);
	}

	@Override
	public void onNotification(NotificationEmittedEvent event)
	{
		publish(event);
	}

	private void publish(LocalEvent event)
	{
		ensureOpen();
		LocalEvent validated = requireSource(event);
		if (!connected)
		{
			return;
		}
		if (eventQueue.offer(new QueuedEvent(new TransportMessage.Event(validated))))
		{
			requestIo();
		}
	}

	private void seed(LocalEvent event)
	{
		ensureOpen();
		LocalEvent validated = requireSource(event);
		String key = stateKey(validated);
		synchronized (stateLock)
		{
			latestStates.put(
				key,
				new StateSnapshot(++stateRevision, new TransportMessage.State(validated))
			);
		}
		requestIo();
	}

	private void publishStateful(LocalEvent event)
	{
		ensureOpen();
		LocalEvent validated = requireSource(event);
		String key = stateKey(validated);
		synchronized (stateLock)
		{
			boolean sessionStateReady = connected && stateReplayCompleteLocked();
			long revision = ++stateRevision;
			latestStates.put(
				key,
				new StateSnapshot(revision, new TransportMessage.State(validated))
			);

			if (sessionStateReady
				&& eventQueue.offer(new QueuedEvent(new TransportMessage.Event(validated))))
			{
				// The live EVENT carries this revision to the consumer. Suppress the
				// matching STATE frame so transition trackers see the old value first.
				sentStateRevisions.put(key, revision);
			}
		}
		requestIo();
	}

	private boolean stateReplayCompleteLocked()
	{
		if (sentResetRevision < resetRevision)
		{
			return false;
		}
		for (Map.Entry<String, StateSnapshot> entry : latestStates.entrySet())
		{
			long sentRevision = sentStateRevisions.getOrDefault(entry.getKey(), 0L);
			if (sentRevision < entry.getValue().revision)
			{
				return false;
			}
		}
		return true;
	}

	private LocalEvent requireSource(LocalEvent event)
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

	private static String stateKey(LocalEvent event)
	{
		if (event instanceof ResourceChangedEvent)
		{
			ResourceChangedEvent resource = (ResourceChangedEvent) event;
			return event.getType() + ":" + resource.getKind().name();
		}
		return event.getType();
	}

	private void requestIo()
	{
		if (closed)
		{
			return;
		}
		cancelReconnect();
		if (ioRunning.compareAndSet(false, true))
		{
			ioExecutor.execute(this::runIo);
		}
	}

	private void runIo()
	{
		try
		{
			if (closed)
			{
				return;
			}

			if (!connected && !connect())
			{
				scheduleReconnect();
				return;
			}

			while (!closed && connected)
			{
				PendingControl control = nextPendingControl();
				if (control != null)
				{
					write(control.message);
					markControlSent(control);
					continue;
				}

				QueuedEvent event = eventQueue.poll();
				if (event == null)
				{
					break;
				}

				write(event.message);
			}
		}
		catch (IOException | RuntimeException ex)
		{
			disconnect();
			scheduleReconnect();
		}
		finally
		{
			ioRunning.set(false);
			if (!closed && connected && hasPendingWork())
			{
				requestIo();
			}
		}
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
				new InetSocketAddress(LoopbackEndpoint.address(), port),
				LoopbackEndpoint.CONNECT_TIMEOUT_MILLIS
			);
			candidate.setTcpNoDelay(true);
			candidate.setSoTimeout(LoopbackEndpoint.HANDSHAKE_TIMEOUT_MILLIS);

			TransportMessage.Hello hello = new TransportMessage.Hello(
				source,
				LocalEventProtocol.NAME,
				LocalEventProtocol.VERSION,
				capabilities
			);
			FrameIo.write(candidate.getOutputStream(), codec.encode(hello));
			String responseFrame = FrameIo.read(candidate.getInputStream());
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
				throw new TransportProtocolException(
					"Local event transport did not acknowledge hello"
				);
			}
			TransportMessage.HelloAck ack = (TransportMessage.HelloAck) response;
			if (!LocalEventProtocol.NAME.equals(ack.getEventProtocol())
				|| ack.getEventVersion() != LocalEventProtocol.VERSION
				|| !capabilities.equals(ack.getCapabilities()))
			{
				throw new TransportProtocolException(
					"Local event transport acknowledged an incompatible source contract"
				);
			}

			candidate.setSoTimeout(0);
			connected = true;
			reconnectDelayMillis = RECONNECT_INITIAL_MILLIS;
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

	private boolean hasPendingWork()
	{
		if (!eventQueue.isEmpty())
		{
			return true;
		}
		synchronized (stateLock)
		{
			if (sentResetRevision < resetRevision)
			{
				return true;
			}
			for (Map.Entry<String, StateSnapshot> entry : latestStates.entrySet())
			{
				long sentRevision = sentStateRevisions.getOrDefault(entry.getKey(), 0L);
				if (sentRevision < entry.getValue().revision)
				{
					return true;
				}
			}
		}
		return false;
	}

	private void write(TransportMessage message) throws IOException
	{
		Socket current = socket;
		if (!connected || current == null || current.isClosed())
		{
			throw new IOException("Local event transport socket is closed");
		}
		FrameIo.write(current.getOutputStream(), codec.encode(message));
	}

	private void scheduleReconnect()
	{
		if (closed || connected)
		{
			return;
		}
		synchronized (scheduleLock)
		{
			if (closed || connected)
			{
				return;
			}
			if (reconnectFuture != null && !reconnectFuture.isDone())
			{
				return;
			}
			long delay = reconnectDelayMillis;
			reconnectDelayMillis = Math.min(
				RECONNECT_MAX_MILLIS,
				reconnectDelayMillis * 2L
			);
			reconnectFuture = ioExecutor.schedule(this::requestIo, delay, TimeUnit.MILLISECONDS);
		}
	}

	private void cancelReconnect()
	{
		synchronized (scheduleLock)
		{
			ScheduledFuture<?> pending = reconnectFuture;
			reconnectFuture = null;
			if (pending != null)
			{
				pending.cancel(false);
			}
		}
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
		cancelReconnect();
		disconnect();
		ioExecutor.shutdownNow();
	}

	private static final class DaemonThreadFactory implements ThreadFactory
	{
		@Override
		public Thread newThread(Runnable runnable)
		{
			Thread thread = new Thread(runnable, "local-event-bridge-io");
			thread.setDaemon(true);
			return thread;
		}
	}

	private static final class QueuedEvent
	{
		private final TransportMessage.Event message;

		private QueuedEvent(TransportMessage.Event message)
		{
			this.message = message;
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
