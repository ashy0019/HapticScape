package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.remote.UnlockKeyProtector;

/** Supplies secret protection for the standalone desktop host. */
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
