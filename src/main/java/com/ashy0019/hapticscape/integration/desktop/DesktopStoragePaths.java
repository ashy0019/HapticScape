package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves desktop-host filesystem locations that are outside HapticScape core. */
public final class DesktopStoragePaths
{
	private DesktopStoragePaths()
	{
	}

	public static HapticScapeStoragePaths hapticScapeStoragePaths()
	{
		return new HapticScapeStoragePaths(applicationDataDirectory());
	}

	public static HapticScapeStoragePaths hapticScapeStoragePaths(String profile)
	{
		return new HapticScapeStoragePaths(applicationDataDirectory(profile));
	}

	public static Path applicationDataDirectory()
	{
		return applicationDataDirectory(
			System.getenv("LOCALAPPDATA"),
			System.getProperty("user.home")
		);
	}

	public static Path applicationDataDirectory(String profile)
	{
		return applicationDataDirectory(
			System.getenv("LOCALAPPDATA"),
			System.getProperty("user.home"),
			profile
		);
	}

	static Path applicationDataDirectory(
		String localApplicationData,
		String userHome,
		String profile)
	{
		Path base = applicationDataDirectory(localApplicationData, userHome);
		return profile == null ? base : base.resolve("profiles").resolve(profile);
	}

	static Path applicationDataDirectory(String localApplicationData, String userHome)
	{
		if (localApplicationData != null && !localApplicationData.trim().isEmpty())
		{
			return Paths.get(localApplicationData, "HapticScape");
		}
		if (userHome == null || userHome.trim().isEmpty())
		{
			throw new IllegalStateException("No per-user desktop storage directory is available");
		}
		return Paths.get(userHome, ".hapticscape");
	}

	public static Path deepLinkInboxPath()
	{
		return applicationDataDirectory().resolve("deep-links");
	}

	public static Path deepLinkInboxPath(String profile)
	{
		return applicationDataDirectory(profile).resolve("deep-links");
	}

	static Path deepLinkInboxPath(String localApplicationData, String userHome)
	{
		return applicationDataDirectory(localApplicationData, userHome).resolve("deep-links");
	}
}
