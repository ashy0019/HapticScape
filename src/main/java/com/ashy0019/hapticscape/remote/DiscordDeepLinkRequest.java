package com.ashy0019.hapticscape.remote;

import java.net.URI;
import java.util.Objects;

/** An opaque, non-authorizing wake request delivered through hapticscape://. */
public final class DiscordDeepLinkRequest
{
	private final String controllerId;
	private final String requestId;
	private final String acceptToken;

	private DiscordDeepLinkRequest(
		String controllerId,
		String requestId,
		String acceptToken)
	{
		this.controllerId = controllerId;
		this.requestId = requestId;
		this.acceptToken = acceptToken;
	}

	public static DiscordDeepLinkRequest parse(String encoded)
	{
		URI uri = URI.create(Objects.requireNonNull(encoded, "deep link").trim());
		if (!"hapticscape".equalsIgnoreCase(uri.getScheme())
			|| !"discord".equalsIgnoreCase(uri.getHost())
			|| uri.getPort() != -1
			|| !"/accept".equals(uri.getPath())
			|| uri.getFragment() != null
			|| uri.getUserInfo() != null)
		{
			throw new IllegalArgumentException("Invalid HapticScape deep link");
		}
		String controller = null;
		String request = null;
		String token = null;
		String query = uri.getRawQuery();
		if (query != null)
		{
			for (String entry : query.split("&", -1))
			{
				String[] pair = entry.split("=", 2);
				if (pair.length != 2)
				{
					throw new IllegalArgumentException("Invalid HapticScape deep link");
				}
				switch (pair[0])
				{
					case "controller":
						controller = unique(controller, pair[1]);
						break;
					case "request":
						request = unique(request, pair[1]);
						break;
					case "token":
						token = unique(token, pair[1]);
						break;
					default:
						throw new IllegalArgumentException("Invalid HapticScape deep link");
				}
			}
		}
		if (controller == null || !controller.matches("[0-9]{15,22}")
			|| request == null || !request.matches("[A-Za-z0-9_-]{16}")
			|| token == null || !token.matches("[A-Za-z0-9_-]{43}"))
		{
			throw new IllegalArgumentException("Invalid HapticScape deep link");
		}
		return new DiscordDeepLinkRequest(controller, request, token);
	}

	public String getControllerId()
	{
		return controllerId;
	}

	public String getRequestId()
	{
		return requestId;
	}

	public String getAcceptToken()
	{
		return acceptToken;
	}

	private static String unique(String current, String next)
	{
		if (current != null || next == null || next.isEmpty())
		{
			throw new IllegalArgumentException("Invalid HapticScape deep link");
		}
		return next;
	}
}
