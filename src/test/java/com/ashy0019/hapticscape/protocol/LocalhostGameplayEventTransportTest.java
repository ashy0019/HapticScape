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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalhostGameplayEventTransportTest
{
	@Test
	public void crossesRealLoopbackSocketAndPreservesOperations() throws Exception
	{
		RecordingSink downstream = new RecordingSink(4);
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		try (LocalhostGameplayEventServer server =
			new LocalhostGameplayEventServer(codec, downstream, 0);
			 LocalhostGameplayEventTransport transport =
				 new LocalhostGameplayEventTransport(
					"runelite",
					codec,
					EnumSet.allOf(SourceCapability.class),
					server.getPort()
				))
		{
			assertEquals(LocalhostTransportEndpoint.HOST, server.getHost());

			transport.onXpEvent(new XpEvent(
				"runelite", "WOODCUTTING", 100, 120, 20, 1, 2
			));
			transport.seedInventory(new InventoryChangedEvent("runelite", 12, 28));
			transport.onPlayerDeathEvent(new PlayerDeathEvent("runelite"));
			transport.resetSourceState();

			assertTrue(downstream.await());
			assertEquals("woodcutting", downstream.xp.getSkillId());
			assertEquals(12, downstream.seededInventory.getFilledSlots());
			assertEquals(1, downstream.deathCount);
			assertEquals(1, downstream.resetCount);
		}
	}

	@Test
	public void reconnectsAndRenegotiatesAfterDetectedSocketBreak() throws Exception
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		RecordingSink firstSink = new RecordingSink(1);
		LocalhostGameplayEventServer firstServer =
			new LocalhostGameplayEventServer(codec, firstSink, 0);
		int port = firstServer.getPort();

		try (LocalhostGameplayEventTransport transport =
			new LocalhostGameplayEventTransport(
				"runelite",
				codec,
				EnumSet.allOf(SourceCapability.class),
				port
			))
		{
			transport.onPlayerDeathEvent(new PlayerDeathEvent("runelite"));
			assertTrue(firstSink.await());
			firstServer.close();

			RecordingSink secondSink = new RecordingSink(1);
			try (LocalhostGameplayEventServer secondServer =
				new LocalhostGameplayEventServer(codec, secondSink, port))
			{
				transport.onXpEvent(new XpEvent(
					"runelite", "AGILITY", 200, 250, 50, 2, 3
				));
				assertTrue(secondSink.await());
				assertEquals("agility", secondSink.xp.getSkillId());
			}
		}
		finally
		{
			firstServer.close();
		}
	}

	private static final class RecordingSink implements GameplayEventSink
	{
		private final CountDownLatch latch;
		private XpEvent xp;
		private InventoryChangedEvent seededInventory;
		private int deathCount;
		private int resetCount;

		private RecordingSink(int expectedCalls)
		{
			latch = new CountDownLatch(expectedCalls);
		}

		private boolean await() throws InterruptedException
		{
			return latch.await(2, TimeUnit.SECONDS);
		}

		private void recorded()
		{
			latch.countDown();
		}

		@Override
		public void resetSourceState()
		{
			resetCount++;
			recorded();
		}

		@Override
		public void seedVitals(VitalsChangedEvent event)
		{
			recorded();
		}

		@Override
		public void seedInventory(InventoryChangedEvent event)
		{
			seededInventory = event;
			recorded();
		}

		@Override
		public void seedToxicStatus(ToxicStatusChangedEvent event)
		{
			recorded();
		}

		@Override
		public void onXpEvent(XpEvent event)
		{
			xp = event;
			recorded();
		}

		@Override
		public void onChatEvent(ChatEvent event)
		{
			recorded();
		}

		@Override
		public void onVitalsEvent(VitalsChangedEvent event)
		{
			recorded();
		}

		@Override
		public void onInventoryEvent(InventoryChangedEvent event)
		{
			recorded();
		}

		@Override
		public void onToxicStatusEvent(ToxicStatusChangedEvent event)
		{
			recorded();
		}

		@Override
		public void onLootEvent(LootReceivedEvent event)
		{
			recorded();
		}

		@Override
		public void onPlayerDeathEvent(PlayerDeathEvent event)
		{
			deathCount++;
			recorded();
		}

		@Override
		public void onNotificationEvent(NotificationEvent event)
		{
			recorded();
		}
	}
}
