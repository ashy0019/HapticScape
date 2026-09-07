package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.NotificationEvent;
import java.util.Objects;
import net.runelite.client.events.NotificationFired;

/** Translates RuneLite notification facts into a source-neutral event. */
public final class RuneLiteNotificationEventAdapter
{
	private static final String SOURCE = "runelite";

	public NotificationEvent adapt(NotificationFired event, boolean sourceFocused)
	{
		Objects.requireNonNull(event, "event");
		return adapt(sourceFocused, event.getNotification().isSendWhenFocused());
	}

	NotificationEvent adapt(boolean sourceFocused, boolean sendWhenFocused)
	{
		return new NotificationEvent(SOURCE, sourceFocused, sendWhenFocused);
	}
}
