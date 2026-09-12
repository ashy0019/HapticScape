package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import com.google.gson.Gson;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class InProcessWireGameplayEventTransportTest
{
	@Test
	public void roundTripsEveryPublishedEventFamilyBeforeDelivery()
	{
		RecordingSink downstream = new RecordingSink();
		InProcessWireGameplayEventTransport transport = transport(downstream);

		XpEvent xp = new XpEvent("test-source", "WOODCUTTING", 100, 120, 20, 1, 2);
		ChatEvent chat = new ChatEvent("test-source", ChatEvent.Kind.OTHER, "raw", "normalized");
		VitalsChangedEvent vitals = new VitalsChangedEvent(
			"test-source",
			VitalsChangedEvent.Kind.HITPOINTS,
			17,
			99
		);
		InventoryChangedEvent inventory = new InventoryChangedEvent("test-source", 28, 28);
		ToxicStatusChangedEvent toxic = new ToxicStatusChangedEvent(
			"test-source",
			ToxicStatusChangedEvent.Status.POISONED
		);
		LootReceivedEvent loot = new LootReceivedEvent("test-source", 2, 123_456L);
		PlayerDeathEvent death = new PlayerDeathEvent("test-source");
		NotificationEvent notification = new NotificationEvent("test-source", true, false);

		transport.onXpEvent(xp);
		transport.onChatEvent(chat);
		transport.onVitalsEvent(vitals);
		transport.onInventoryEvent(inventory);
		transport.onToxicStatusEvent(toxic);
		transport.onLootEvent(loot);
		transport.onPlayerDeathEvent(death);
		transport.onNotificationEvent(notification);

		assertNotSame(xp, downstream.xp);
		assertEquals("woodcutting", downstream.xp.getSkillId());
		assertNotSame(chat, downstream.chat);
		assertEquals("normalized", downstream.chat.getNormalizedMessage());
		assertNotSame(vitals, downstream.vitals);
		assertNotSame(inventory, downstream.inventory);
		assertNotSame(toxic, downstream.toxic);
		assertNotSame(loot, downstream.loot);
		assertNotSame(death, downstream.death);
		assertNotSame(notification, downstream.notification);
	}

	@Test
	public void seedEventsRoundTripButRemainSeedOperations()
	{
		RecordingSink downstream = new RecordingSink();
		InProcessWireGameplayEventTransport transport = transport(downstream);

		VitalsChangedEvent vitals = new VitalsChangedEvent(
			"test-source",
			VitalsChangedEvent.Kind.PRAYER,
			50,
			77
		);
		InventoryChangedEvent inventory = new InventoryChangedEvent("test-source", 12, 28);
		ToxicStatusChangedEvent toxic = new ToxicStatusChangedEvent(
			"test-source",
			ToxicStatusChangedEvent.Status.CLEAR
		);

		transport.seedVitals(vitals);
		transport.seedInventory(inventory);
		transport.seedToxicStatus(toxic);

		assertNotSame(vitals, downstream.seededVitals);
		assertNotSame(inventory, downstream.seededInventory);
		assertNotSame(toxic, downstream.seededToxic);
		assertEquals(0, downstream.publishedVitalsCount);
		assertEquals(0, downstream.publishedInventoryCount);
		assertEquals(0, downstream.publishedToxicCount);
	}

	@Test
	public void resetSourceStateRoundTripsAsAControlSignal()
	{
		RecordingSink downstream = new RecordingSink();
		InProcessWireGameplayEventTransport transport = transport(downstream);

		transport.resetSourceState();

		assertEquals(1, downstream.resetCount);
	}

	private static InProcessWireGameplayEventTransport transport(GameplayEventSink downstream)
	{
		return new InProcessWireGameplayEventTransport(
			"test-source",
			new TransportWireCodec(new Gson()),
			EnumSet.allOf(SourceCapability.class),
			downstream
		);
	}

	private static final class RecordingSink implements GameplayEventSink
	{
		private int resetCount;
		private int publishedVitalsCount;
		private int publishedInventoryCount;
		private int publishedToxicCount;
		private VitalsChangedEvent seededVitals;
		private InventoryChangedEvent seededInventory;
		private ToxicStatusChangedEvent seededToxic;
		private XpEvent xp;
		private ChatEvent chat;
		private VitalsChangedEvent vitals;
		private InventoryChangedEvent inventory;
		private ToxicStatusChangedEvent toxic;
		private LootReceivedEvent loot;
		private PlayerDeathEvent death;
		private NotificationEvent notification;

		@Override
		public void resetSourceState()
		{
			resetCount++;
		}

		@Override
		public void seedVitals(VitalsChangedEvent event)
		{
			seededVitals = event;
		}

		@Override
		public void seedInventory(InventoryChangedEvent event)
		{
			seededInventory = event;
		}

		@Override
		public void seedToxicStatus(ToxicStatusChangedEvent event)
		{
			seededToxic = event;
		}

		@Override
		public void onXpEvent(XpEvent event)
		{
			xp = event;
		}

		@Override
		public void onChatEvent(ChatEvent event)
		{
			chat = event;
		}

		@Override
		public void onVitalsEvent(VitalsChangedEvent event)
		{
			vitals = event;
			publishedVitalsCount++;
		}

		@Override
		public void onInventoryEvent(InventoryChangedEvent event)
		{
			inventory = event;
			publishedInventoryCount++;
		}

		@Override
		public void onToxicStatusEvent(ToxicStatusChangedEvent event)
		{
			toxic = event;
			publishedToxicCount++;
		}

		@Override
		public void onLootEvent(LootReceivedEvent event)
		{
			loot = event;
		}

		@Override
		public void onPlayerDeathEvent(PlayerDeathEvent event)
		{
			death = event;
		}

		@Override
		public void onNotificationEvent(NotificationEvent event)
		{
			notification = event;
		}
	}
}
