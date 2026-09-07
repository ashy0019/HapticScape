package com.ashy0019.hapticscape.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationEventTest
{
	@Test
	public void preservesNeutralNotificationFacts()
	{
		NotificationEvent event = new NotificationEvent("runelite", true, false);

		assertEquals("runelite", event.getSource());
		assertEquals(NotificationEvent.TYPE, event.getType());
		assertTrue(event.isSourceFocused());
		assertFalse(event.isSendWhenFocused());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsBlankSource()
	{
		new NotificationEvent("  ", false, false);
	}
}
