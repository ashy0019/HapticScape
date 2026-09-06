package com.ashy0019.hapticscape.remote;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Publishes encrypted invitations to short-lived, one-use relay mailboxes. */
public final class RemotePairingService
{
	private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");
	private static final String USER_AGENT = "HapticScape-Pairing";

	private final OkHttpClient httpClient;

	public RemotePairingService(OkHttpClient httpClient)
	{
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
	}

	public CompletableFuture<RemotePairingCode> publish(RemoteInvitation invitation)
	{
		RemoteInvitation required = Objects.requireNonNull(invitation, "invitation");
		RemotePairingCode code = RemotePairingCode.generate();
		String envelope = new RemoteCrypto(code.encryptionKey()).encrypt(required.encode());
		Request request = new Request.Builder()
			.url(pairingEndpoint(required.getRelayUrl(), code.locator()))
			.header("User-Agent", USER_AGENT)
			.header("X-HapticScape-Redeem-Proof", code.redeemProof())
			.header("X-HapticScape-Cancel-Proof", code.cancelProof())
			.put(RequestBody.create(TEXT, envelope))
			.build();

		CompletableFuture<RemotePairingCode> result = new CompletableFuture<>();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				result.completeExceptionally(new IllegalStateException(
					"The pairing service could not be reached",
					exception
				));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (closeable.code() != 201)
					{
						result.completeExceptionally(new IllegalStateException(
							"The pairing service rejected the connection code (HTTP "
								+ closeable.code() + ")"
						));
						return;
					}
					result.complete(code);
				}
			}
		});
		return result;
	}

	public CompletableFuture<RemoteInvitation> redeem(
		String relayUrl,
		String encodedCode)
	{
		RemotePairingCode code = RemotePairingCode.parse(encodedCode);
		Request request = new Request.Builder()
			.url(pairingEndpoint(relayUrl, code.locator()))
			.header("User-Agent", USER_AGENT)
			.header("X-HapticScape-Redeem-Proof", code.redeemProof())
			.get()
			.build();

		CompletableFuture<RemoteInvitation> result = new CompletableFuture<>();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				result.completeExceptionally(new IllegalStateException(
					"The pairing service could not be reached",
					exception
				));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (closeable.code() == 404)
					{
						result.completeExceptionally(new IllegalStateException(
							"This connection code has expired or was already used"
						));
						return;
					}
					if (!closeable.isSuccessful() || closeable.body() == null)
					{
						result.completeExceptionally(new IllegalStateException(
							"The pairing service rejected the connection code (HTTP "
								+ closeable.code() + ")"
						));
						return;
					}
					try
					{
						String encodedInvitation = new RemoteCrypto(code.encryptionKey())
							.decrypt(closeable.body().string());
						RemoteInvitation invitation = RemoteInvitation.parse(encodedInvitation);
						if (!sameRelayOrigin(relayUrl, invitation.getRelayUrl()))
						{
							throw new IllegalArgumentException(
								"The connection code returned an unexpected relay"
							);
						}
						result.complete(invitation);
					}
					catch (RuntimeException | IOException exception)
					{
						result.completeExceptionally(new IllegalArgumentException(
							"The connection code contained an invalid invitation",
							exception
						));
					}
				}
			}
		});
		return result;
	}

	public CompletableFuture<Void> cancel(String relayUrl, RemotePairingCode code)
	{
		RemotePairingCode required = Objects.requireNonNull(code, "code");
		Request request = new Request.Builder()
			.url(pairingEndpoint(relayUrl, required.locator()))
			.header("User-Agent", USER_AGENT)
			.header("X-HapticScape-Cancel-Proof", required.cancelProof())
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
					if (closeable.isSuccessful() || closeable.code() == 404)
					{
						result.complete(null);
					}
					else
					{
						result.completeExceptionally(new IllegalStateException(
							"The pairing service rejected cancellation"
						));
					}
				}
			}
		});
		return result;
	}

	static String pairingEndpoint(String relayUrl, String locator)
	{
		return serviceEndpoint(
			relayUrl,
			"/pairing/" + Objects.requireNonNull(locator, "locator")
		);
	}

	public static String discordInstallEndpoint(String relayUrl)
	{
		return serviceEndpoint(relayUrl, "/discord/install");
	}

	static String serviceEndpoint(String relayUrl, String path)
	{
		URI relay;
		try
		{
			relay = new URI(Objects.requireNonNull(relayUrl, "relayUrl").trim());
		}
		catch (URISyntaxException | NullPointerException exception)
		{
			throw new IllegalArgumentException("Remote relay URL is invalid", exception);
		}

		String scheme;
		if ("wss".equalsIgnoreCase(relay.getScheme()))
		{
			scheme = "https";
		}
		else if ("ws".equalsIgnoreCase(relay.getScheme()) && isLoopback(relay.getHost()))
		{
			scheme = "http";
		}
		else
		{
			throw new IllegalArgumentException(
				"Remote relay URL must use wss:// for connection codes"
			);
		}
		if (relay.getHost() == null || relay.getHost().trim().isEmpty())
		{
			throw new IllegalArgumentException("Remote relay URL must include a host");
		}

		try
		{
			return new URI(
				scheme,
				relay.getUserInfo(),
				relay.getHost(),
				relay.getPort(),
				Objects.requireNonNull(path, "path"),
				null,
				null
			).toASCIIString();
		}
		catch (URISyntaxException exception)
		{
			throw new IllegalArgumentException("Unable to build pairing-service URL", exception);
		}
	}

	static String webSocketEndpoint(String relayUrl, String path, String query)
	{
		URI relay;
		try
		{
			relay = new URI(Objects.requireNonNull(relayUrl, "relayUrl").trim());
		}
		catch (URISyntaxException | NullPointerException exception)
		{
			throw new IllegalArgumentException("Remote relay URL is invalid", exception);
		}
		String scheme = relay.getScheme();
		if (!"wss".equalsIgnoreCase(scheme)
			&& !("ws".equalsIgnoreCase(scheme) && isLoopback(relay.getHost())))
		{
			throw new IllegalArgumentException(
				"Remote relay URL must use wss:// for Discord linking"
			);
		}
		if (relay.getHost() == null || relay.getHost().trim().isEmpty())
		{
			throw new IllegalArgumentException("Remote relay URL must include a host");
		}
		try
		{
			return new URI(
				scheme.toLowerCase(),
				relay.getUserInfo(),
				relay.getHost(),
				relay.getPort(),
				Objects.requireNonNull(path, "path"),
				query,
				null
			).toASCIIString();
		}
		catch (URISyntaxException exception)
		{
			throw new IllegalArgumentException("Unable to build Discord device URL", exception);
		}
	}

	private static boolean sameRelayOrigin(String expected, String actual)
	{
		URI first = URI.create(pairingEndpoint(expected, "origin-check"));
		URI second = URI.create(pairingEndpoint(actual, "origin-check"));
		return first.getScheme().equalsIgnoreCase(second.getScheme())
			&& first.getHost().equalsIgnoreCase(second.getHost())
			&& effectivePort(first) == effectivePort(second);
	}

	private static int effectivePort(URI uri)
	{
		if (uri.getPort() >= 0)
		{
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static boolean isLoopback(String host)
	{
		return "localhost".equalsIgnoreCase(host)
			|| "127.0.0.1".equals(host)
			|| "::1".equals(host);
	}
}
