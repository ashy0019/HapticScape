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
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransportSessionReceiverTest
{
	private static final Set<SourceCapability> CAPABILITIES = EnumSet.of(
		SourceCapability.INVENTORY_OCCUPANCY,
		SourceCapability.ACTOR_DEATH
	);

	@Test
	public void requiresHelloAndNegotiatesSourceCapabilities()
	{
		RecordingSink sink = new RecordingSink();
		TransportSessionReceiver receiver = new TransportSessionReceiver(sink);

		TransportMessage response = receiver.receive(new TransportMessage.Reset("test-source"));
		assertError("hello_required", response);
		assertFalse(receiver.isReady());

		response = receiver.receive(new TransportMessage.Hello(
			"test-source",
			EventProtocol.NAME,
			EventProtocol.VERSION,
			CAPABILITIES
		));
		assertTrue(response instanceof TransportMessage.HelloAck);
		assertTrue(receiver.isReady());
		assertEquals("test-source", receiver.getSource());
		assertEquals(CAPABILITIES, receiver.getCapabilities());
		assertEquals(CAPABILITIES, ((TransportMessage.HelloAck) response).getCapabilities());
	}

	@Test
	public void acceptsLegacyEventProtocolAndMirrorsItInHelloAck()
	{
		TransportSessionReceiver receiver = new TransportSessionReceiver(new RecordingSink());
		TransportMessage response = receiver.receive(new TransportMessage.Hello(
			"legacy-source",
			EventProtocol.LEGACY_NAME,
			EventProtocol.VERSION,
			CAPABILITIES
		));

		assertTrue(response instanceof TransportMessage.HelloAck);
		assertEquals(
			EventProtocol.LEGACY_NAME,
			((TransportMessage.HelloAck) response).getEventProtocol()
		);
	}

	@Test
	public void rejectsUnsupportedEventVersion()
	{
		TransportSessionReceiver receiver = new TransportSessionReceiver(new RecordingSink());
		TransportMessage response = receiver.receive(new TransportMessage.Hello(
			"test-source",
			EventProtocol.NAME,
			EventProtocol.VERSION + 1,
			CAPABILITIES
		));

		assertError("unsupported_event_version", response);
		assertFalse(receiver.isReady());
	}

	@Test
	public void dispatchesEventStateAndResetAfterHello()
	{
		RecordingSink sink = new RecordingSink();
		TransportSessionReceiver receiver = ready(sink);

		assertNull(receiver.receive(new TransportMessage.Event(
			new PlayerDeathEvent("test-source")
		)));
		assertEquals(1, sink.deathCount);

		assertNull(receiver.receive(new TransportMessage.State(
			new InventoryChangedEvent("test-source", 12, 28)
		)));
		assertEquals(1, sink.seedInventoryCount);

		assertNull(receiver.receive(new TransportMessage.Reset("test-source")));
		assertEquals(1, sink.resetCount);
	}

	@Test
	public void rejectsMessagesFromAnotherSource()
	{
		RecordingSink sink = new RecordingSink();
		TransportSessionReceiver receiver = ready(sink);

		TransportMessage response = receiver.receive(new TransportMessage.Event(
			new PlayerDeathEvent("other-source")
		));
		assertError("source_mismatch", response);
		assertEquals(0, sink.deathCount);

		response = receiver.receive(new TransportMessage.Reset("other-source"));
		assertError("source_mismatch", response);
		assertEquals(0, sink.resetCount);
	}

	@Test
	public void rejectsEventsOutsideAdvertisedCapabilities()
	{
		TransportSessionReceiver receiver = ready(new RecordingSink());
		TransportMessage response = receiver.receive(new TransportMessage.Event(
			new XpEvent("test-source", "attack", 1, 2, 1, 1, 1)
		));
		assertError("capability_not_declared", response);
	}

	@Test
	public void rejectsInvalidStateEventFamily()
	{
		TransportSessionReceiver receiver = ready(new RecordingSink());
		TransportMessage response = receiver.receive(new TransportMessage.State(
			new PlayerDeathEvent("test-source")
		));
		assertError("invalid_state", response);
	}

	private static TransportSessionReceiver ready(RecordingSink sink)
	{
		TransportSessionReceiver receiver = new TransportSessionReceiver(sink);
		TransportMessage response = receiver.receive(new TransportMessage.Hello(
			"test-source",
			EventProtocol.NAME,
			EventProtocol.VERSION,
			CAPABILITIES
		));
		assertTrue(response instanceof TransportMessage.HelloAck);
		return receiver;
	}

	private static void assertError(String code, TransportMessage message)
	{
		assertTrue(message instanceof TransportMessage.Error);
		assertEquals(code, ((TransportMessage.Error) message).getCode());
	}

	private static final class RecordingSink implements GameplayEventSink
	{
		private int resetCount;
		private int seedInventoryCount;
		private int deathCount;

		@Override
		public void resetSourceState()
		{
			resetCount++;
		}

		@Override
		public void seedVitals(VitalsChangedEvent event)
		{
		}

		@Override
		public void seedInventory(InventoryChangedEvent event)
		{
			seedInventoryCount++;
		}

		@Override
		public void seedToxicStatus(ToxicStatusChangedEvent event)
		{
		}

		@Override
		public void onXpEvent(XpEvent event)
		{
		}

		@Override
		public void onChatEvent(ChatEvent event)
		{
		}

		@Override
		public void onVitalsEvent(VitalsChangedEvent event)
		{
		}

		@Override
		public void onInventoryEvent(InventoryChangedEvent event)
		{
		}

		@Override
		public void onToxicStatusEvent(ToxicStatusChangedEvent event)
		{
		}

		@Override
		public void onLootEvent(LootReceivedEvent event)
		{
		}

		@Override
		public void onPlayerDeathEvent(PlayerDeathEvent event)
		{
			deathCount++;
		}

		@Override
		public void onNotificationEvent(NotificationEvent event)
		{
		}
	}
}
