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
import java.io.IOException;
import java.net.ServerSocket;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
					"test-source",
					codec,
					EnumSet.allOf(SourceCapability.class),
					server.getPort()
				))
		{
			assertEquals(LocalhostTransportEndpoint.HOST, server.getHost());
			assertTrue(awaitConnected(transport));

			transport.onXpEvent(new XpEvent(
				"test-source", "WOODCUTTING", 100, 120, 20, 1, 2
			));
			transport.seedInventory(new InventoryChangedEvent("test-source", 12, 28));
			transport.onPlayerDeathEvent(new PlayerDeathEvent("test-source"));

			assertTrue(downstream.await());
			assertEquals("woodcutting", downstream.xp.getSkillId());
			assertEquals(12, downstream.seededInventory.getFilledSlots());
			assertEquals(1, downstream.deathCount);
			assertEquals(1, downstream.resetCount);
		}
	}

	@Test
	public void startsWithoutReceiverAndReplaysOnlyLatestStateWhenItAppears() throws Exception
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		int port = unusedLoopbackPort();
		try (LocalhostGameplayEventTransport transport =
			new LocalhostGameplayEventTransport(
				"test-source",
				codec,
				EnumSet.allOf(SourceCapability.class),
				port
			))
		{
			transport.seedInventory(new InventoryChangedEvent("test-source", 5, 28));
			transport.seedInventory(new InventoryChangedEvent("test-source", 17, 28));
			transport.onPlayerDeathEvent(new PlayerDeathEvent("test-source"));
			assertFalse(transport.isConnected());

			RecordingSink downstream = new RecordingSink(2);
			try (LocalhostGameplayEventServer server =
				new LocalhostGameplayEventServer(codec, downstream, port))
			{
				assertTrue(awaitConnected(transport));
				assertTrue(downstream.await());
				assertEquals(17, downstream.seededInventory.getFilledSlots());
				assertEquals(0, downstream.deathCount);
				assertEquals(1, downstream.resetCount);
			}
		}
	}

	@Test
	public void reconnectsWithResetAndRetainedStateWithoutReplayingTransientEvents() throws Exception
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		RecordingSink firstSink = new RecordingSink(2);
		LocalhostGameplayEventServer firstServer =
			new LocalhostGameplayEventServer(codec, firstSink, 0);
		int port = firstServer.getPort();

		try (LocalhostGameplayEventTransport transport =
			new LocalhostGameplayEventTransport(
				"test-source",
				codec,
				EnumSet.allOf(SourceCapability.class),
				port
			))
		{
			assertTrue(awaitConnected(transport));
			transport.seedInventory(new InventoryChangedEvent("test-source", 12, 28));
			assertTrue(firstSink.await());
			firstServer.close();

			for (int i = 0; i < 20 && transport.isConnected(); i++)
			{
				transport.onPlayerDeathEvent(new PlayerDeathEvent("test-source"));
				Thread.sleep(50L);
			}
			assertTrue(awaitDisconnected(transport));

			transport.onPlayerDeathEvent(new PlayerDeathEvent("test-source"));
			transport.onInventoryEvent(new InventoryChangedEvent("test-source", 19, 28));
			RecordingSink secondSink = new RecordingSink(2);
			try (LocalhostGameplayEventServer secondServer =
				new LocalhostGameplayEventServer(codec, secondSink, port))
			{
				assertTrue(awaitConnected(transport));
				assertTrue(secondSink.await());
				assertEquals(19, secondSink.seededInventory.getFilledSlots());
				assertEquals(0, secondSink.deathCount);
				assertEquals(1, secondSink.resetCount);
			}
		}
		finally
		{
			firstServer.close();
		}
	}

	private static boolean awaitConnected(LocalhostGameplayEventTransport transport)
		throws InterruptedException
	{
		return awaitConnectionState(transport, true);
	}

	private static boolean awaitDisconnected(LocalhostGameplayEventTransport transport)
		throws InterruptedException
	{
		return awaitConnectionState(transport, false);
	}

	private static boolean awaitConnectionState(
		LocalhostGameplayEventTransport transport,
		boolean expected) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline)
		{
			if (transport.isConnected() == expected)
			{
				return true;
			}
			Thread.sleep(25L);
		}
		return transport.isConnected() == expected;
	}

	private static int unusedLoopbackPort() throws IOException
	{
		try (ServerSocket socket = new ServerSocket(0, 1, LocalhostTransportEndpoint.address()))
		{
			return socket.getLocalPort();
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
			return latch.await(5, TimeUnit.SECONDS);
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
