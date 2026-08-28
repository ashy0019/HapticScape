package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationFeedbackSettingsTest
{
	@Test
	public void disabledFeedbackNeverPlays()
	{
		NotificationFeedbackSettings settings = settings(false, false);

		assertFalse(settings.shouldPlay(false, true));
	}

	@Test
	public void focusedClientIsSuppressedWhenRuneLiteWouldSuppressNotification()
	{
		NotificationFeedbackSettings settings = settings(true, true);

		assertFalse(settings.shouldPlay(true, false));
	}

	@Test
	public void unfocusedClientPlaysWhenRespectingRuneLiteFocus()
	{
		NotificationFeedbackSettings settings = settings(true, true);

		assertTrue(settings.shouldPlay(false, false));
	}

	@Test
	public void focusedClientPlaysWhenNotificationAllowsIt()
	{
		NotificationFeedbackSettings settings = settings(true, true);

		assertTrue(settings.shouldPlay(true, true));
	}

	@Test
	public void disablingFocusRuleAllowsFeedbackWhileFocused()
	{
		NotificationFeedbackSettings settings = settings(true, false);

		assertTrue(settings.shouldPlay(true, false));
	}

	@Test
	public void preservesConfiguredPatternValues()
	{
		NotificationFeedbackSettings settings = new NotificationFeedbackSettings(
			true,
			true,
			47,
			650,
			HapticPatternSelection.custom(3)
		);

		assertTrue(settings.isEnabled());
		assertTrue(settings.isRespectRuneLiteFocus());
		assertEquals(47, settings.getIntensityPercent());
		assertEquals(650, settings.getDurationMillis());
		assertEquals(HapticPatternSelection.custom(3), settings.getPatternSelection());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsOutOfRangeIntensity()
	{
		new NotificationFeedbackSettings(
			true,
			true,
			101,
			500,
			HapticPatternSelection.DOUBLE
		);
	}

	private static NotificationFeedbackSettings settings(
		boolean enabled,
		boolean respectRuneLiteFocus)
	{
		return new NotificationFeedbackSettings(
			enabled,
			respectRuneLiteFocus,
			50,
			500,
			HapticPatternSelection.DOUBLE
		);
	}
}
