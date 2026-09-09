package com.ashy0019.hapticscape.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HapticScapeDesktopWindowSizingTest
{
	@Test
	public void defaultsToTheReferenceDesktopComposition()
	{
		assertEquals(1024, HapticScapeDesktopWindow.DEFAULT_WINDOW_WIDTH);
		assertEquals(900, HapticScapeDesktopWindow.DEFAULT_WINDOW_HEIGHT);
		assertTrue(
			HapticScapeDesktopWindow.MINIMUM_WINDOW_WIDTH
				< HapticScapeDesktopWindow.DEFAULT_WINDOW_WIDTH
		);
		assertTrue(
			HapticScapeDesktopWindow.MINIMUM_WINDOW_HEIGHT
				< HapticScapeDesktopWindow.DEFAULT_WINDOW_HEIGHT
		);
	}

	@Test
	public void unauthorizedExitAllowsTheQueuedFlagToFlush()
	{
		assertTrue(HapticScapeDesktopWindow.UNAUTHORIZED_EXIT_FLUSH_MILLIS >= 500);
		assertTrue(HapticScapeDesktopWindow.UNAUTHORIZED_EXIT_FLUSH_MILLIS <= 2_000);
	}
}
