package com.ashy0019.localeventbridge.protocol;

import com.ashy0019.localeventbridge.event.ExperienceChangedEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProtocolCompatibilityTest
{
	@Test
	public void helloUsesStableVersionOneContract()
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		String json = codec.encode(new TransportMessage.Hello(
			"runelite",
			LocalEventProtocol.NAME,
			LocalEventProtocol.VERSION,
			EnumSet.allOf(SourceCapability.class)
		));

		JsonObject root = new JsonParser().parse(json).getAsJsonObject();
		assertEquals("local-event-bridge", root.get("protocol").getAsString());
		assertEquals(1, root.get("version").getAsInt());
		assertEquals("hello", root.get("kind").getAsString());

		JsonObject payload = root.getAsJsonObject("payload");
		assertEquals("runelite", payload.get("source").getAsString());
		assertEquals("local-event-bridge-events", payload.get("eventProtocol").getAsString());
		assertEquals(1, payload.get("eventVersion").getAsInt());

		JsonArray capabilities = payload.getAsJsonArray("capabilities");
		assertEquals(SourceCapability.values().length, capabilities.size());
		assertTrue(capabilities.toString().contains("\"inventory.occupancy\""));
		assertTrue(capabilities.toString().contains("\"actor.death\""));
	}

	@Test
	public void experienceEventMatchesConsumerWireShape()
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		String json = codec.encode(new TransportMessage.Event(
			new ExperienceChangedEvent(
				"runelite",
				"COOKING",
				100,
				120,
				20,
				1,
				2
			)
		));

		JsonObject root = new JsonParser().parse(json).getAsJsonObject();
		assertEquals("event", root.get("kind").getAsString());

		JsonObject event = root
			.getAsJsonObject("payload")
			.getAsJsonObject("event");
		assertEquals("local-event-bridge-events", event.get("protocol").getAsString());
		assertEquals(1, event.get("version").getAsInt());
		assertEquals("runelite", event.get("source").getAsString());
		assertEquals("experience.changed", event.get("type").getAsString());

		JsonObject payload = event.getAsJsonObject("payload");
		assertEquals("cooking", payload.get("skillId").getAsString());
		assertEquals(100, payload.get("previousXp").getAsInt());
		assertEquals(120, payload.get("currentXp").getAsInt());
		assertEquals(20, payload.get("gainedXp").getAsInt());
		assertEquals(1, payload.get("previousLevel").getAsInt());
		assertEquals(2, payload.get("currentLevel").getAsInt());
	}

	@Test
	public void transportKindSurfaceContainsNoGameplayCommand()
	{
		assertEquals(6, TransportMessage.Kind.values().length);
		assertEquals(TransportMessage.Kind.HELLO, TransportMessage.Kind.valueOf("HELLO"));
		assertEquals(TransportMessage.Kind.HELLO_ACK, TransportMessage.Kind.valueOf("HELLO_ACK"));
		assertEquals(TransportMessage.Kind.EVENT, TransportMessage.Kind.valueOf("EVENT"));
		assertEquals(TransportMessage.Kind.STATE, TransportMessage.Kind.valueOf("STATE"));
		assertEquals(TransportMessage.Kind.RESET, TransportMessage.Kind.valueOf("RESET"));
		assertEquals(TransportMessage.Kind.ERROR, TransportMessage.Kind.valueOf("ERROR"));
	}

	@Test
	public void acceptsVersionOneHelloAck()
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		TransportMessage decoded = codec.decode(codec.encode(
			new TransportMessage.HelloAck(
				LocalEventProtocol.NAME,
				LocalEventProtocol.VERSION,
				EnumSet.allOf(SourceCapability.class)
			)
		));

		assertTrue(decoded instanceof TransportMessage.HelloAck);
		TransportMessage.HelloAck ack = (TransportMessage.HelloAck) decoded;
		assertEquals(LocalEventProtocol.NAME, ack.getEventProtocol());
		assertEquals(LocalEventProtocol.VERSION, ack.getEventVersion());
		assertEquals(EnumSet.allOf(SourceCapability.class), ack.getCapabilities());
	}
}
