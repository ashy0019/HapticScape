package com.ashy0019.hapticscape.integration.desktop;

import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DesktopStoragePathsTest
{
	@Test
	public void prefersLocalApplicationDataForApplicationDataDirectory()
	{
		assertEquals(
			Paths.get("local-app-data", "HapticScape"),
			DesktopStoragePaths.applicationDataDirectory("local-app-data", "home")
		);
	}

	@Test
	public void fallsBackToUserHomeForApplicationDataDirectory()
	{
		assertEquals(
			Paths.get("home", ".hapticscape"),
			DesktopStoragePaths.applicationDataDirectory(null, "home")
		);
	}

	@Test
	public void fallsBackToUserHomeWhenLocalApplicationDataIsBlank()
	{
		assertEquals(
			Paths.get("home", ".hapticscape"),
			DesktopStoragePaths.applicationDataDirectory("   ", "home")
		);
	}

	@Test
	public void deepLinkInboxLivesUnderApplicationDataDirectory()
	{
		assertEquals(
			Paths.get("local-app-data", "HapticScape", "deep-links"),
			DesktopStoragePaths.deepLinkInboxPath("local-app-data", "home")
		);
	}
}
