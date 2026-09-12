package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.GameplayEventSink;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/**
 * Loopback-only receiver for the game-agnostic local event transport.
 *
 * <p>One source connection is handled at a time. Each TCP connection gets a
 * fresh transport session and must negotiate hello before sending gameplay.</p>
 */
public final class LocalhostGameplayEventServer implements AutoCloseable
{
	private final TransportWireCodec codec;
	private final GameplayEventSink downstream;
	private final ServerSocket serverSocket;
	private final Thread acceptThread;
	private volatile boolean closed;
	private volatile Socket activeSocket;

	public LocalhostGameplayEventServer(
		TransportWireCodec codec,
		GameplayEventSink downstream,
		int port)
	{
		this.codec = Objects.requireNonNull(codec, "codec");
		this.downstream = Objects.requireNonNull(downstream, "downstream");
		if (port < 0 || port > 65_535)
		{
			throw new IllegalArgumentException("port must be between 0 and 65535");
		}

		try
		{
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(LocalhostTransportEndpoint.address(), port), 1);
		}
		catch (IOException ex)
		{
			throw new TransportProtocolException(
				"Unable to bind local event transport on "
					+ LocalhostTransportEndpoint.HOST + ":" + port,
				ex
			);
		}

		acceptThread = new Thread(this::run, "HapticScape-local-gameplay-server");
		acceptThread.setDaemon(true);
		acceptThread.start();
	}

	public int getPort()
	{
		return serverSocket.getLocalPort();
	}

	public String getHost()
	{
		return LocalhostTransportEndpoint.HOST;
	}

	private void run()
	{
		while (!closed)
		{
			try
			{
				Socket socket = serverSocket.accept();
				if (!socket.getInetAddress().isLoopbackAddress())
				{
					socket.close();
					continue;
				}
				activeSocket = socket;
				handle(socket);
			}
			catch (SocketException ex)
			{
				if (!closed)
				{
					closeActiveSocket();
				}
			}
			catch (IOException ex)
			{
				closeActiveSocket();
			}
			finally
			{
				activeSocket = null;
			}
		}
	}

	private void handle(Socket socket) throws IOException
	{
		socket.setTcpNoDelay(true);
		TransportSessionReceiver receiver = new TransportSessionReceiver(downstream);
		String negotiatedTransportProtocol = null;
		while (!closed && !socket.isClosed())
		{
			String frame = LocalhostFrameIo.read(socket.getInputStream());
			if (frame == null)
			{
				return;
			}

			final TransportMessage request;
			try
			{
				TransportWireCodec.DecodedFrame decoded = codec.decodeFrame(frame);
				request = decoded.getMessage();
				if (negotiatedTransportProtocol == null)
				{
					negotiatedTransportProtocol = decoded.getProtocol();
				}
				else if (!negotiatedTransportProtocol.equals(decoded.getProtocol()))
				{
					writeError(
						socket,
						negotiatedTransportProtocol,
						"protocol_changed",
						"Transport protocol identifier changed during the session"
					);
					return;
				}
			}
			catch (RuntimeException ex)
			{
				writeError(
					socket,
					negotiatedTransportProtocol,
					"invalid_frame",
					ex.getMessage()
				);
				return;
			}

			final TransportMessage response;
			try
			{
				response = receiver.receive(request);
			}
			catch (RuntimeException ex)
			{
				writeError(
					socket,
					negotiatedTransportProtocol,
					"receiver_error",
					ex.getMessage()
				);
				return;
			}
			if (response != null)
			{
				LocalhostFrameIo.write(
					socket.getOutputStream(),
					codec.encode(response, negotiatedTransportProtocol)
				);
				if (response instanceof TransportMessage.Error)
				{
					return;
				}
			}
		}
	}

	private void writeError(
		Socket socket,
		String protocolName,
		String code,
		String message)
	{
		try
		{
			String responseProtocol = protocolName == null
				? TransportProtocol.NAME
				: protocolName;
			LocalhostFrameIo.write(
				socket.getOutputStream(),
				codec.encode(
					new TransportMessage.Error(code, message == null ? "" : message),
					responseProtocol
				)
			);
		}
		catch (IOException ignored)
		{
			// The peer is already unusable; there is nowhere else to report this.
		}
	}

	@Override
	public void close()
	{
		closed = true;
		closeActiveSocket();
		try
		{
			serverSocket.close();
		}
		catch (IOException ignored)
		{
			// Best-effort shutdown.
		}
		if (Thread.currentThread() != acceptThread)
		{
			try
			{
				acceptThread.join(1_000L);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private void closeActiveSocket()
	{
		Socket socket = activeSocket;
		if (socket == null)
		{
			return;
		}
		try
		{
			try
			{
				socket.setSoLinger(true, 0);
			}
			catch (SocketException ignored)
			{
				// Closing the socket is still sufficient for normal shutdown.
			}
			socket.close();
		}
		catch (IOException ignored)
		{
			// Best-effort shutdown.
		}
	}
}
