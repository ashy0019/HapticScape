package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.NotificationEvent;
import java.util.Objects;

/**
 * Decides whether a source-neutral notification has any enabled output channel.
 */
public final class GenericNotificationDecision
{
	private GenericNotificationDecision()
	{
	}

	public static boolean shouldDispatch(
		NotificationEvent event,
		NotificationFeedbackSettings hapticSettings,
		boolean clickEnabled)
	{
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(hapticSettings, "hapticSettings");
		return (hapticSettings.isEnabled() || clickEnabled)
			&& hapticSettings.allowsFocus(
				event.isSourceFocused(),
				event.isSendWhenFocused()
			);
	}
}
