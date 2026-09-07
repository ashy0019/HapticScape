package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.remote.UnlockKeyProtector;

/** Supplies desktop-specific secret protection to RuneLite-hosted HapticScape. */
public final class DesktopSecretProtectors
{
	private DesktopSecretProtectors()
	{
	}

	public static UnlockKeyProtector savedUnlockKeys()
	{
		return new WindowsDpapiUnlockKeyProtector();
	}

	public static UnlockKeyProtector discordCredentials()
	{
		return new WindowsDpapiDiscordCredentialProtector();
	}
}
