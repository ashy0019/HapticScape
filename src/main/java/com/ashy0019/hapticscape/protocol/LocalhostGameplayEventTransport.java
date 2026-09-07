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
import java.util.Objects;

/** Loopback TCP client that publishes neutral gameplay events to HapticScape. */
public final class LocalhostGameplayEventTransport implements GameplayEventSink, AutoCloseable
{
	private final Object ioLock = new Object();
	private final String source;
	private final TransportWireCodec codec;
	private final int port;
	private Socket socket;
	private boolean closed;

	public LocalhostGameplayEventTransport(
		String source,
		TransportWireCodec codec,
		int port)
	{
		this.source = TransportMessage.requireIdentifier(source, "source");
		this.codec = Objects.requireNonNull(codec, "codec");
		if (port <= 0 || port > 65_535)
		{
			throw new IllegalArgumentException("port must be between 1 and 65535");
		}
		this.port = port;
		synchronized (ioLock)
		{
			connectLocked();
		}
	}

	@Override
	public void resetSourceState()
	{
		send(new TransportMessage.Reset(source));
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
		publish(event);
	}

	@Override
	public void onInventoryEvent(InventoryChangedEvent event)
	{
		publish(event);
	}

	@Override
	public void onToxicStatusEvent(ToxicStatusChangedEvent event)
	{
		publish(event);
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
		send(new TransportMessage.Event(
			TransportMessage.EventOperation.PUBLISH,
			requireSource(event)
		));
	}

	private void seed(HapticScapeEvent event)
	{
		send(new TransportMessage.Event(
			TransportMessage.EventOperation.SEED,
			requireSource(event)
		));
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

	private void send(TransportMessage message)
	{
		String frame = codec.encode(message);
		synchronized (ioLock)
		{
			ensureOpen();
			try
			{
				writeLocked(frame);
				return;
			}
			catch (IOException firstFailure)
			{
				closeSocketLocked();
			}

			connectLocked();
			try
			{
				writeLocked(frame);
			}
			catch (IOException retryFailure)
			{
				closeSocketLocked();
				throw new TransportProtocolException(
					"Unable to send local gameplay transport frame after reconnect",
					retryFailure
				);
			}
		}
	}

	private void connectLocked()
	{
		ensureOpen();
		Socket connected = new Socket();
		try
		{
			connected.connect(
				new InetSocketAddress(LocalhostTransportEndpoint.address(), port),
				LocalhostTransportEndpoint.CONNECT_TIMEOUT_MILLIS
			);
			connected.setTcpNoDelay(true);
			connected.setSoTimeout(LocalhostTransportEndpoint.HANDSHAKE_TIMEOUT_MILLIS);

			TransportMessage.Hello hello = new TransportMessage.Hello(
				source,
				EventProtocol.NAME,
				EventProtocol.VERSION
			);
			LocalhostFrameIo.write(connected.getOutputStream(), codec.encode(hello));
			String responseFrame = LocalhostFrameIo.read(connected.getInputStream());
			if (responseFrame == null)
			{
				throw new TransportProtocolException("Local gameplay transport closed during hello");
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
				throw new TransportProtocolException("Local gameplay transport did not acknowledge hello");
			}
			TransportMessage.HelloAck ack = (TransportMessage.HelloAck) response;
			if (!EventProtocol.NAME.equals(ack.getEventProtocol())
				|| ack.getEventVersion() != EventProtocol.VERSION)
			{
				throw new TransportProtocolException(
					"Local gameplay transport acknowledged incompatible event protocol"
				);
			}
			connected.setSoTimeout(0);
			socket = connected;
		}
		catch (IOException | RuntimeException ex)
		{
			try
			{
				connected.close();
			}
			catch (IOException ignored)
			{
				// Preserve the connection failure.
			}
			if (ex instanceof TransportProtocolException)
			{
				throw (TransportProtocolException) ex;
			}
			throw new TransportProtocolException(
				"Unable to connect local gameplay transport to "
					+ LocalhostTransportEndpoint.HOST + ":" + port,
				ex
			);
		}
	}

	private void writeLocked(String frame) throws IOException
	{
		if (socket == null || socket.isClosed())
		{
			throw new IOException("Local gameplay transport socket is closed");
		}
		LocalhostFrameIo.write(socket.getOutputStream(), frame);
	}

	private void ensureOpen()
	{
		if (closed)
		{
			throw new TransportProtocolException("Local gameplay transport is closed");
		}
	}

	@Override
	public void close()
	{
		synchronized (ioLock)
		{
			closed = true;
			closeSocketLocked();
		}
	}

	private void closeSocketLocked()
	{
		Socket current = socket;
		socket = null;
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
}
