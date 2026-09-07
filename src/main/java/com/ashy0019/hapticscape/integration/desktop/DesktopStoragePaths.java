package com.ashy0019.hapticscape.integration.desktop;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves desktop-host filesystem locations that are outside HapticScape core. */
public final class DesktopStoragePaths
{
	private DesktopStoragePaths()
	{
	}

	public static Path deepLinkInboxPath()
	{
		return deepLinkInboxPath(
			System.getenv("LOCALAPPDATA"),
			System.getProperty("user.home")
		);
	}

	static Path deepLinkInboxPath(String localApplicationData, String userHome)
	{
		if (localApplicationData != null && !localApplicationData.trim().isEmpty())
		{
			return Paths.get(localApplicationData, "HapticScape", "deep-links");
		}
		if (userHome == null || userHome.trim().isEmpty())
		{
			throw new IllegalStateException("No per-user desktop storage directory is available");
		}
		return Paths.get(userHome, ".hapticscape", "deep-links");
	}
}
