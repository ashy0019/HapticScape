package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenericNotificationDecisionTest
{
	@Test
	public void clickOnlyNotificationIsDispatched()
	{
		assertTrue(GenericNotificationDecision.shouldDispatch(
			settings(false, true),
			true,
			false,
			false
		));
	}

	@Test
	public void hapticOnlyNotificationIsDispatched()
	{
		assertTrue(GenericNotificationDecision.shouldDispatch(
			settings(true, true),
			false,
			false,
			false
		));
	}

	@Test
	public void notificationWithNoOutputIsIgnored()
	{
		assertFalse(GenericNotificationDecision.shouldDispatch(
			settings(false, false),
			false,
			false,
			true
		));
	}

	@Test
	public void focusRuleAlsoAppliesToClickOnlyNotification()
	{
		assertFalse(GenericNotificationDecision.shouldDispatch(
			settings(false, true),
			true,
			true,
			false
		));
	}

	private static NotificationFeedbackSettings settings(
		boolean hapticEnabled,
		boolean respectFocus)
	{
		return new NotificationFeedbackSettings(
			hapticEnabled,
			respectFocus,
			50,
			500,
			HapticPatternSelection.DOUBLE
		);
	}
}
