package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.NotificationEmittedEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteNotificationAdapterTest
{
	private final RuneLiteNotificationAdapter adapter = new RuneLiteNotificationAdapter();

	@Test
	public void preservesSourceFocusState()
	{
		NotificationEmittedEvent event = adapter.adapt(true, false);

		assertTrue(event.isSourceFocused());
		assertFalse(event.isSendWhenFocused());
	}

	@Test
	public void preservesSendWhenFocusedPolicy()
	{
		NotificationEmittedEvent event = adapter.adapt(false, true);

		assertFalse(event.isSourceFocused());
		assertTrue(event.isSendWhenFocused());
	}
}
