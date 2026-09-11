package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.NotificationEmittedEvent;
import java.util.Objects;
import net.runelite.client.events.NotificationFired;

/** Translates RuneLite notification facts into a source-neutral event. */
public final class RuneLiteNotificationAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;

	public NotificationEmittedEvent adapt(NotificationFired event, boolean sourceFocused)
	{
		Objects.requireNonNull(event, "event");
		return adapt(sourceFocused, event.getNotification().isSendWhenFocused());
	}

	NotificationEmittedEvent adapt(boolean sourceFocused, boolean sendWhenFocused)
	{
		return new NotificationEmittedEvent(SOURCE, sourceFocused, sendWhenFocused);
	}
}
