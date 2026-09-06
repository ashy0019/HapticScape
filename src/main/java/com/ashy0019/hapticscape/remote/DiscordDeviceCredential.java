package com.ashy0019.hapticscape.remote;

import java.util.Objects;

final class DiscordDeviceCredential
{
	private final String userId;
	private final String displayName;
	private final String relayUrl;
	private final String secret;

	DiscordDeviceCredential(
		String userId,
		String displayName,
		String relayUrl,
		String secret)
	{
		this.userId = requireText(userId, "Discord user ID");
		this.displayName = requireText(displayName, "Discord display name");
		this.relayUrl = requireText(relayUrl, "Discord relay URL");
		this.secret = requireText(secret, "Discord device credential");
		validate();
	}

	String getUserId()
	{
		return userId;
	}

	String getDisplayName()
	{
		return displayName;
	}

	String getRelayUrl()
	{
		return relayUrl;
	}

	String getSecret()
	{
		return secret;
	}

	void validate()
	{
		if (!userId.matches("\\d{15,22}"))
		{
			throw new IllegalArgumentException("Invalid Discord user ID");
		}
		if (displayName.length() > 80)
		{
			throw new IllegalArgumentException("Invalid Discord display name");
		}
		if (!secret.matches("[A-Za-z0-9_-]{43}"))
		{
			throw new IllegalArgumentException("Invalid Discord device credential");
		}
		RemotePairingService.serviceEndpoint(relayUrl, "/discord/link/check");
	}

	private static String requireText(String value, String name)
	{
		String result = Objects.requireNonNull(value, name).trim();
		if (result.isEmpty())
		{
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return result;
	}
}
