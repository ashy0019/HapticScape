package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Connects a locally linked Discord identity to the existing one-use pairing flow. */
public final class DiscordPairingBridge implements AutoCloseable, RemoteSessionListener
{
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final String USER_AGENT = "HapticScape-Discord-Bridge";
	private static final long[] RECONNECT_DELAYS_SECONDS = {2, 5, 15, 30, 60};

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final RemoteSessionManager sessionManager;
	private final RemotePairingService pairingService;
	private final DiscordCredentialStore credentialStore;
	private final ScheduledExecutorService scheduler;
	private final CopyOnWriteArrayList<DiscordLinkListener> listeners =
		new CopyOnWriteArrayList<>();
	private final SecureRandom random = new SecureRandom();
	private final AtomicBoolean pairingInProgress = new AtomicBoolean();

	private volatile DiscordLinkSnapshot snapshot;
	private volatile DiscordDeviceCredential credential;
	private volatile WebSocket socket;
	private volatile boolean closed;
	private volatile boolean manualSocketClose;
	private volatile int reconnectAttempt;
	private RemotePairingCode activePairingCode;
	private String activePairingRelayUrl;
	private String activePairingRequestId;

	public DiscordPairingBridge(
		OkHttpClient httpClient,
		Gson gson,
		RemoteSessionManager sessionManager,
		RemotePairingService pairingService,
		DiscordCredentialStore credentialStore)
	{
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.gson = Objects.requireNonNull(gson, "gson");
		this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
		this.pairingService = Objects.requireNonNull(pairingService, "pairingService");
		this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
		this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
		this.snapshot = credentialStore.isAvailable()
			? unlinkedSnapshot()
			: new DiscordLinkSnapshot(
				DiscordLinkState.UNAVAILABLE,
				"",
				credentialStore.getUnavailableMessage()
			);
	}

	public synchronized void start()
	{
		ensureOpen();
		if (!credentialStore.isAvailable())
		{
			return;
		}
		credential = credentialStore.get().orElse(null);
		sessionManager.addListener(this);
		if (credential != null)
		{
			connectDevice();
		}
	}

	public DiscordLinkSnapshot getSnapshot()
	{
		return snapshot;
	}

	public void addListener(DiscordLinkListener listener)
	{
		DiscordLinkListener required = Objects.requireNonNull(listener, "listener");
		listeners.addIfAbsent(required);
		required.onDiscordLinkChanged(snapshot);
	}

	public void removeListener(DiscordLinkListener listener)
	{
		listeners.remove(listener);
	}

	public CompletableFuture<DiscordLinkSnapshot> link(
		String relayUrl,
		String encodedLinkCode)
	{
		ensureOpen();
		if (!credentialStore.isAvailable())
		{
			throw new IllegalStateException(credentialStore.getUnavailableMessage());
		}
		if (credential != null)
		{
			throw new IllegalStateException("Unlink the current Discord account first");
		}

		String locator = parseLinkCode(encodedLinkCode);
		String secret = generateDeviceSecret();
		String resolvedRelay = Objects.requireNonNull(relayUrl, "relayUrl").trim();
		Request request = new Request.Builder()
			.url(RemotePairingService.serviceEndpoint(
				resolvedRelay,
				"/discord/link/" + locator
			))
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + secret)
			.post(okhttp3.RequestBody.create(null, new byte[0]))
			.build();

		publish(new DiscordLinkSnapshot(
			DiscordLinkState.CONNECTING,
			"",
			"Linking Discord..."
		));
		CompletableFuture<DiscordLinkSnapshot> result = new CompletableFuture<>();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				publish(unlinkedSnapshot());
				result.completeExceptionally(new IllegalStateException(
					"The Discord link service could not be reached",
					exception
				));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (!closeable.isSuccessful() || closeable.body() == null)
					{
						publish(unlinkedSnapshot());
						result.completeExceptionally(new IllegalStateException(
							readError(closeable, "Discord rejected the link code")
						));
						return;
					}
					LinkResult linked = gson.fromJson(closeable.body().charStream(), LinkResult.class);
					DiscordDeviceCredential next = new DiscordDeviceCredential(
						linked.userId,
						linked.displayName,
						resolvedRelay,
						secret
					);
					credentialStore.save(next);
					credential = next;
					reconnectAttempt = 0;
					connectDevice();
					result.complete(snapshot);
				}
				catch (RuntimeException | IOException exception)
				{
					publish(unlinkedSnapshot());
					result.completeExceptionally(new IllegalStateException(
						"HapticScape could not save the Discord device link",
						exception
					));
				}
			}
		});
		return result;
	}

	public CompletableFuture<Void> unlink()
	{
		ensureOpen();
		DiscordDeviceCredential current = credential;
		credentialStore.clear();
		closeSocket();
		credential = null;
		reconnectAttempt = 0;
		cancelActivePairing();
		publish(unlinkedSnapshot());
		if (current == null)
		{
			return CompletableFuture.completedFuture(null);
		}

		String url = RemotePairingService.serviceEndpoint(
			current.getRelayUrl(),
			"/discord/device"
		) + "?user=" + encode(current.getUserId());
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + current.getSecret())
			.delete()
			.build();
		CompletableFuture<Void> result = new CompletableFuture<>();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				result.completeExceptionally(exception);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (closeable.isSuccessful() || closeable.code() == 401)
					{
						result.complete(null);
					}
					else
					{
						result.completeExceptionally(new IllegalStateException(
							"Discord rejected the unlink request"
						));
					}
				}
			}
		});
		return result;
	}

	@Override
	public void onRemoteSessionChanged(RemoteSessionSnapshot session)
	{
		if (session.getState() == RemoteSessionState.LOCAL)
		{
			cancelActivePairing();
		}
	}

	@Override
	public synchronized void close()
	{
		if (closed)
		{
			return;
		}
		closed = true;
		sessionManager.removeListener(this);
		closeSocket();
		cancelActivePairing();
		scheduler.shutdownNow();
		listeners.clear();
	}

	private synchronized void connectDevice()
	{
		if (closed || credential == null || socket != null)
		{
			return;
		}
		DiscordDeviceCredential current = credential;
		String query = "user=" + encode(current.getUserId());
		String url = RemotePairingService.webSocketEndpoint(
			current.getRelayUrl(),
			"/discord/device",
			query
		);
		manualSocketClose = false;
		publish(new DiscordLinkSnapshot(
			DiscordLinkState.CONNECTING,
			current.getDisplayName(),
			"Connecting Discord..."
		));
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + current.getSecret())
			.build();
		socket = httpClient.newWebSocket(request, new DeviceSocketListener());
	}

	private void handlePairingRequest(String text)
	{
		PairRequest request;
		try
		{
			request = gson.fromJson(text, PairRequest.class);
		}
		catch (RuntimeException exception)
		{
			return;
		}
		if (request == null
			|| request.requestId == null
			|| !request.requestId.matches("[A-Za-z0-9_-]{16}"))
		{
			return;
		}
		if ("PAIR_DELIVERY_FAILED".equals(request.type))
		{
			boolean matches;
			synchronized (this)
			{
				matches = request.requestId.equals(activePairingRequestId);
			}
			if (matches)
			{
				cancelActivePairing();
				if (sessionManager.isControllerSession())
				{
					sessionManager.endSession();
				}
			}
			return;
		}
		if (!"PAIR_REQUEST".equals(request.type))
		{
			return;
		}
		if (!pairingInProgress.compareAndSet(false, true))
		{
			sendError(request.requestId, "Another Discord pairing request is already running");
			return;
		}

		RemoteSessionSnapshot session = sessionManager.getSnapshot();
		if (session.getState() != RemoteSessionState.LOCAL)
		{
			pairingInProgress.set(false);
			sendError(request.requestId, "End the current Remote Play session first");
			return;
		}
		DiscordDeviceCredential current = credential;
		if (current == null)
		{
			pairingInProgress.set(false);
			sendError(request.requestId, "This HapticScape client is no longer linked");
			return;
		}

		try
		{
			RemoteInvitation invitation = sessionManager.startController(current.getRelayUrl());
			pairingService.publish(invitation).whenComplete((code, error) ->
				finishPairingRequest(request.requestId, current.getRelayUrl(), code, error)
			);
		}
		catch (RuntimeException exception)
		{
			pairingInProgress.set(false);
			sendError(request.requestId, rootMessage(exception));
		}
	}

	private void finishPairingRequest(
		String requestId,
		String relayUrl,
		RemotePairingCode code,
		Throwable error)
	{
		pairingInProgress.set(false);
		if (error != null || code == null)
		{
			sessionManager.endSession();
			sendError(
				requestId,
				error == null ? "The connection code was not created" : rootMessage(error)
			);
			return;
		}
		if (!sessionManager.isControllerSession())
		{
			pairingService.cancel(relayUrl, code);
			sendError(requestId, "The Remote Play session ended before delivery");
			return;
		}

		synchronized (this)
		{
			activePairingCode = code;
			activePairingRelayUrl = relayUrl;
			activePairingRequestId = requestId;
		}
		if (!send(new PairResponse("PAIR_RESPONSE", requestId, code.encode(), null)))
		{
			cancelActivePairing();
			sessionManager.endSession();
		}
	}

	private void sendError(String requestId, String message)
	{
		send(new PairResponse("PAIR_ERROR", requestId, null, message));
	}

	private boolean send(Object message)
	{
		WebSocket current = socket;
		return current != null && current.send(gson.toJson(message));
	}

	private synchronized void cancelActivePairing()
	{
		RemotePairingCode code = activePairingCode;
		String relayUrl = activePairingRelayUrl;
		activePairingCode = null;
		activePairingRelayUrl = null;
		activePairingRequestId = null;
		if (code != null && relayUrl != null)
		{
			pairingService.cancel(relayUrl, code);
		}
	}

	private synchronized void socketEnded(WebSocket ended, int code)
	{
		if (socket != ended)
		{
			return;
		}
		socket = null;
		if (closed || manualSocketClose)
		{
			return;
		}
		if (code == 4003 || code == 401)
		{
			credential = null;
			try
			{
				credentialStore.clear();
			}
			catch (RuntimeException ignored)
			{
				// The remote revocation remains authoritative.
			}
			publish(unlinkedSnapshot());
			return;
		}
		DiscordDeviceCredential current = credential;
		if (current != null)
		{
			publish(new DiscordLinkSnapshot(
				DiscordLinkState.OFFLINE,
				current.getDisplayName(),
				"Discord link offline; reconnecting..."
			));
			scheduleReconnect();
		}
	}

	private synchronized void scheduleReconnect()
	{
		if (closed || credential == null)
		{
			return;
		}
		int index = Math.min(reconnectAttempt, RECONNECT_DELAYS_SECONDS.length - 1);
		long delay = RECONNECT_DELAYS_SECONDS[index];
		reconnectAttempt++;
		scheduler.schedule(this::connectDevice, delay, TimeUnit.SECONDS);
	}

	private synchronized void closeSocket()
	{
		manualSocketClose = true;
		WebSocket current = socket;
		socket = null;
		if (current != null && !current.close(1000, "HapticScape Discord bridge closed"))
		{
			current.cancel();
		}
	}

	private void publish(DiscordLinkSnapshot next)
	{
		snapshot = next;
		for (DiscordLinkListener listener : listeners)
		{
			try
			{
				listener.onDiscordLinkChanged(next);
			}
			catch (RuntimeException ignored)
			{
				// A UI listener must not break the device channel.
			}
		}
	}

	private void ensureOpen()
	{
		if (closed)
		{
			throw new IllegalStateException("Discord pairing bridge is closed");
		}
	}

	private static DiscordLinkSnapshot unlinkedSnapshot()
	{
		return new DiscordLinkSnapshot(
			DiscordLinkState.UNLINKED,
			"",
			"Discord is not linked"
		);
	}

	private static String parseLinkCode(String encoded)
	{
		String value = Objects.requireNonNull(encoded, "Discord link code").trim();
		if (!value.startsWith("HSL1."))
		{
			throw new IllegalArgumentException("Invalid Discord link code");
		}
		String locator = value.substring("HSL1.".length());
		if (!locator.matches("[A-Za-z0-9_-]{16}"))
		{
			throw new IllegalArgumentException("Invalid Discord link code");
		}
		return locator;
	}

	private String generateDeviceSecret()
	{
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		try
		{
			return ENCODER.encodeToString(bytes);
		}
		finally
		{
			java.util.Arrays.fill(bytes, (byte) 0);
		}
	}

	private static String readError(Response response, String fallback) throws IOException
	{
		if (response.body() == null)
		{
			return fallback + " (HTTP " + response.code() + ")";
		}
		String body = response.body().string();
		int marker = body.indexOf("\"error\"");
		if (marker >= 0)
		{
			int colon = body.indexOf(':', marker);
			int firstQuote = body.indexOf('"', colon + 1);
			int secondQuote = body.indexOf('"', firstQuote + 1);
			if (firstQuote >= 0 && secondQuote > firstQuote)
			{
				return body.substring(firstQuote + 1, secondQuote);
			}
		}
		return fallback + " (HTTP " + response.code() + ")";
	}

	private static String rootMessage(Throwable throwable)
	{
		Throwable current = throwable;
		while (current.getCause() != null)
		{
			current = current.getCause();
		}
		String message = current.getMessage();
		return message == null || message.trim().isEmpty()
			? "Discord pairing failed"
			: message;
	}

	private static String encode(String value)
	{
		try
		{
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("Unable to encode Discord device URL", exception);
		}
	}

	private final class DeviceSocketListener extends WebSocketListener
	{
		@Override
		public void onOpen(WebSocket webSocket, Response response)
		{
			if (socket != webSocket || credential == null || closed)
			{
				webSocket.close(1000, "HapticScape Discord bridge no longer active");
				return;
			}
			reconnectAttempt = 0;
			DiscordDeviceCredential current = credential;
			if (current != null)
			{
				publish(new DiscordLinkSnapshot(
					DiscordLinkState.LINKED,
					current.getDisplayName(),
					"Discord linked as " + current.getDisplayName()
				));
			}
		}

		@Override
		public void onMessage(WebSocket webSocket, String text)
		{
			handlePairingRequest(text);
		}

		@Override
		public void onClosed(WebSocket webSocket, int code, String reason)
		{
			socketEnded(webSocket, code);
		}

		@Override
		public void onFailure(WebSocket webSocket, Throwable error, Response response)
		{
			socketEnded(webSocket, response == null ? 0 : response.code());
		}
	}

	private static final class PairRequest
	{
		private String type;
		private String requestId;
	}

	private static final class PairResponse
	{
		private final String type;
		private final String requestId;
		private final String code;
		private final String message;

		private PairResponse(String type, String requestId, String code, String message)
		{
			this.type = type;
			this.requestId = requestId;
			this.code = code;
			this.message = message;
		}
	}

	private static final class LinkResult
	{
		private String userId;
		private String displayName;
	}

	private static final class DaemonThreadFactory implements ThreadFactory
	{
		@Override
		public Thread newThread(Runnable runnable)
		{
			Thread thread = new Thread(runnable, "hapticscape-discord-reconnect");
			thread.setDaemon(true);
			return thread;
		}
	}
}
