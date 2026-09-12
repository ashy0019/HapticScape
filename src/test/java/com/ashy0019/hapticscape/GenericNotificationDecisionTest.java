package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.NotificationEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenericNotificationDecisionTest
{
	@Test
	public void clickOnlyNotificationIsDispatched()
	{
		assertTrue(GenericNotificationDecision.shouldDispatch(
			event(false, false),
			settings(false, true),
			true
		));
	}

	@Test
	public void hapticOnlyNotificationIsDispatched()
	{
		assertTrue(GenericNotificationDecision.shouldDispatch(
			event(false, false),
			settings(true, true),
			false
		));
	}

	@Test
	public void notificationWithNoOutputIsIgnored()
	{
		assertFalse(GenericNotificationDecision.shouldDispatch(
			event(false, true),
			settings(false, false),
			false
		));
	}

	@Test
	public void focusRuleAlsoAppliesToClickOnlyNotification()
	{
		assertFalse(GenericNotificationDecision.shouldDispatch(
			event(true, false),
			settings(false, true),
			true
		));
	}

	private static NotificationEvent event(boolean sourceFocused, boolean sendWhenFocused)
	{
		return new NotificationEvent("test", sourceFocused, sendWhenFocused);
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
