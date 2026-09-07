package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.NotificationEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteNotificationEventAdapterTest
{
	private final RuneLiteNotificationEventAdapter adapter = new RuneLiteNotificationEventAdapter();

	@Test
	public void preservesSourceFocusState()
	{
		NotificationEvent event = adapter.adapt(true, false);

		assertTrue(event.isSourceFocused());
		assertFalse(event.isSendWhenFocused());
	}

	@Test
	public void preservesSendWhenFocusedPolicy()
	{
		NotificationEvent event = adapter.adapt(false, true);

		assertFalse(event.isSourceFocused());
		assertTrue(event.isSendWhenFocused());
	}
}
