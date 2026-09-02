package com.ashy0019.hapticscape;

import java.util.Objects;

/**
 * Decides whether a RuneLite notification has any enabled output channel.
 */
public final class GenericNotificationDecision
{
	private GenericNotificationDecision()
	{
	}

	public static boolean shouldDispatch(
		NotificationFeedbackSettings hapticSettings,
		boolean clickEnabled,
		boolean runeLiteFocused,
		boolean notificationSendsWhenFocused)
	{
		Objects.requireNonNull(hapticSettings, "hapticSettings");
		return (hapticSettings.isEnabled() || clickEnabled)
			&& hapticSettings.allowsFocus(
				runeLiteFocused,
				notificationSendsWhenFocused
			);
	}
}
