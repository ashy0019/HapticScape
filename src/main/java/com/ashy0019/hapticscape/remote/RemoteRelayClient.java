package com.ashy0019.hapticscape.remote;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class RemoteRelayClient implements AutoCloseable
{
	interface Listener
	{
		void onOpen();

		void onMessage(String message);

		void onClosed(String reason);

		void onFailure(String message, Throwable error);
	}

	private final OkHttpClient httpClient;
	private final Listener listener;
	private final AtomicBoolean manualClose = new AtomicBoolean();
	private volatile WebSocket socket;

	RemoteRelayClient(OkHttpClient httpClient, Listener listener)
	{
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.listener = Objects.requireNonNull(listener, "listener");
	}

	synchronized void connect(String relayUrl, String roomId, RemoteRole role)
	{
		if (socket != null)
		{
			throw new IllegalStateException("Remote relay connection is already open");
		}
		String base = requireRelayUrl(relayUrl);
		String separator = base.contains("?") ? "&" : "?";
		String url = base
			+ separator + "room=" + encode(roomId)
			+ "&role=" + encode(role.name().toLowerCase());

		manualClose.set(false);
		Request request = new Request.Builder().url(url).build();
		socket = httpClient.newWebSocket(request, new SocketListener());
	}

	boolean send(String message)
	{
		WebSocket current = socket;
		return current != null && current.send(message);
	}

	boolean isOpen()
	{
		return socket != null;
	}

	@Override
	public synchronized void close()
	{
		manualClose.set(true);
		WebSocket current = socket;
		socket = null;
		if (current != null && !current.close(1000, "HapticScape remote session ended"))
		{
			current.cancel();
		}
	}

	private static String requireRelayUrl(String relayUrl)
	{
		String value = Objects.requireNonNull(relayUrl, "relayUrl").trim();
		URI uri;
		try
		{
			uri = URI.create(value);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalArgumentException("Remote relay URL is invalid", e);
		}

		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (host == null || host.trim().isEmpty())
		{
			throw new IllegalArgumentException("Remote relay URL must include a host");
		}
		if ("wss".equalsIgnoreCase(scheme))
		{
			return value;
		}
		if ("ws".equalsIgnoreCase(scheme) && isLoopbackHost(host))
		{
			return value;
		}
		throw new IllegalArgumentException(
			"Remote relay URL must use wss:// (ws:// is allowed only for localhost development)"
		);
	}

	private static boolean isLoopbackHost(String host)
	{
		return "localhost".equalsIgnoreCase(host)
			|| "127.0.0.1".equals(host)
			|| "::1".equals(host);
	}

	private static String encode(String value)
	{
		try
		{
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Unable to encode remote relay URL", e);
		}
	}

	private final class SocketListener extends WebSocketListener
	{
		@Override
		public void onOpen(WebSocket webSocket, Response response)
		{
			listener.onOpen();
		}

		@Override
		public void onMessage(WebSocket webSocket, String text)
		{
			listener.onMessage(text);
		}

		@Override
		public void onClosed(WebSocket webSocket, int code, String reason)
		{
			socket = null;
			if (!manualClose.get())
			{
				listener.onClosed(reason == null ? "Remote relay closed" : reason);
			}
		}

		@Override
		public void onFailure(WebSocket webSocket, Throwable error, Response response)
		{
			socket = null;
			if (!manualClose.get())
			{
				String message = error == null || error.getMessage() == null
					? "Remote relay connection failed"
					: error.getMessage();
				listener.onFailure(message, error);
			}
		}
	}
}
