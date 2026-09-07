package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.remote.DiscordDeepLinkInbox;

/** Owns the process-wide Discord deep-link inbox for the desktop host. */
public final class DesktopDiscordDeepLinkInbox
{
	private static final DiscordDeepLinkInbox INSTANCE =
		new DiscordDeepLinkInbox(DesktopStoragePaths.deepLinkInboxPath());

	private DesktopDiscordDeepLinkInbox()
	{
	}

	public static DiscordDeepLinkInbox getInstance()
	{
		return INSTANCE;
	}
}
