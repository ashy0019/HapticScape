package com.ashy0019.hapticscape.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DesktopLaunchOptionsTest
{
	@Test
	public void ordinaryLaunchPreservesProductionDefaults()
	{
		DesktopLaunchOptions options = DesktopLaunchOptions.parse(new String[0]);

		assertFalse(options.isNamedProfile());
		assertEquals(null, options.getProfile());
		assertEquals(41713, options.getGameplayPort());
		assertEquals("HapticScape", options.getWindowTitle());
		assertFalse(options.isMinimized());
	}

	@Test
	public void startupLaunchCanBeginInTray()
	{
		DesktopLaunchOptions options = DesktopLaunchOptions.parse(
			new String[] {"--minimized"}
		);
		assertTrue(options.isMinimized());
		assertFalse(options.isNamedProfile());
	}

	@Test
	public void namedClientGetsIndependentIdentityAndPort()
	{
		DesktopLaunchOptions options = DesktopLaunchOptions.parse(new String[]
		{
			"--profile", "Controller", "--gameplay-port=41714"
		});

		assertTrue(options.isNamedProfile());
		assertEquals("controller", options.getProfile());
		assertEquals(41714, options.getGameplayPort());
		assertEquals("HapticScape — controller", options.getWindowTitle());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsProfilePathTraversal()
	{
		DesktopLaunchOptions.parse(new String[] {"--profile", "../subject"});
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsInvalidPort()
	{
		DesktopLaunchOptions.parse(new String[] {"--gameplay-port", "70000"});
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsDuplicateOptions()
	{
		DesktopLaunchOptions.parse(new String[]
		{
			"--profile=controller", "--profile=subject"
		});
	}
}
