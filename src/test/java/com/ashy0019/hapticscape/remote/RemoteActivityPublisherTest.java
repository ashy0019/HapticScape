package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RemoteActivityPublisherTest
{
	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-09-11T23:00:00Z"),
		ZoneOffset.UTC
	);

	@Test
	public void xpEventsBecomeOneCompactActivityFact()
	{
		List<RemoteActivityEvent> events = new ArrayList<>();
		RemoteActivityPublisher publisher = new RemoteActivityPublisher(events::add, FIXED_CLOCK);

		publisher.onXpEvent(new XpEvent("bridge", "ranged", 1_000, 1_044, 44, 20, 20));
		publisher.onXpEvent(new XpEvent("bridge", "ranged", 2_000, 2_100, 100, 29, 30));

		assertEquals(2, events.size());
		assertEquals(RemoteActivityType.XP_GAIN, events.get(0).getType());
		assertEquals("Ranged", events.get(0).getLabel());
		assertEquals("+44 XP", events.get(0).getDetail());
		assertEquals(RemoteActivityType.MILESTONE, events.get(1).getType());
		assertEquals("Level 30 • +100 XP", events.get(1).getDetail());
		assertEquals(FIXED_CLOCK.millis(), events.get(0).getTimestampMillis());
	}

	@Test
	public void chatActivityNeverContainsChatText()
	{
		List<RemoteActivityEvent> events = new ArrayList<>();
		RemoteActivityPublisher publisher = new RemoteActivityPublisher(events::add, FIXED_CLOCK);
		String secret = "this raw message must stay local";

		publisher.onChatEvent(new ChatEvent(
			"bridge",
			ChatEvent.Kind.DIRECT_MESSAGE,
			secret,
			secret
		));
		publisher.onChatEvent(new ChatEvent(
			"bridge",
			ChatEvent.Kind.OTHER,
			secret,
			secret
		));

		assertEquals(1, events.size());
		RemoteActivityEvent event = events.get(0);
		assertEquals(RemoteActivityType.DIRECT_MESSAGE, event.getType());
		assertEquals("Direct message", event.getLabel());
		assertEquals("Received", event.getDetail());
		assertFalse(event.getLabel().contains(secret));
		assertFalse(event.getDetail().contains(secret));
	}

	@Test
	public void inventoryFullOnlyPublishesOnTransition()
	{
		List<RemoteActivityEvent> events = new ArrayList<>();
		RemoteActivityPublisher publisher = new RemoteActivityPublisher(events::add, FIXED_CLOCK);
		publisher.seedInventory(new InventoryChangedEvent("bridge", 27, 28));

		publisher.onInventoryEvent(new InventoryChangedEvent("bridge", 28, 28));
		publisher.onInventoryEvent(new InventoryChangedEvent("bridge", 28, 28));
		publisher.onInventoryEvent(new InventoryChangedEvent("bridge", 27, 28));
		publisher.onInventoryEvent(new InventoryChangedEvent("bridge", 28, 28));

		assertEquals(2, events.size());
		assertEquals(RemoteActivityType.INVENTORY_FULL, events.get(0).getType());
		assertEquals("Inventory Full", events.get(0).getLabel());
		assertEquals("", events.get(0).getDetail());
	}
}
