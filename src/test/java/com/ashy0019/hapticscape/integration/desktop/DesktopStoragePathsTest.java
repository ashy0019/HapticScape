package com.ashy0019.hapticscape.integration.desktop;

import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DesktopStoragePathsTest
{
	@Test
	public void prefersLocalApplicationDataForDeepLinkInbox()
	{
		assertEquals(
			Paths.get("local-app-data", "HapticScape", "deep-links"),
			DesktopStoragePaths.deepLinkInboxPath("local-app-data", "home")
		);
	}

	@Test
	public void fallsBackToUserHomeWhenLocalApplicationDataIsMissing()
	{
		assertEquals(
			Paths.get("home", ".hapticscape", "deep-links"),
			DesktopStoragePaths.deepLinkInboxPath(null, "home")
		);
	}

	@Test
	public void fallsBackToUserHomeWhenLocalApplicationDataIsBlank()
	{
		assertEquals(
			Paths.get("home", ".hapticscape", "deep-links"),
			DesktopStoragePaths.deepLinkInboxPath("   ", "home")
		);
	}
}
