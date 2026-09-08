package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportWireCodecTest
{
	private final TransportWireCodec codec = new TransportWireCodec(new Gson());
	private final Set<SourceCapability> capabilities = EnumSet.of(
		SourceCapability.EXPERIENCE,
		SourceCapability.RESOURCES,
		SourceCapability.ACTOR_DEATH
	);

	@Test
	public void writesStableTransportEnvelopeAndCapabilities()
	{
		String json = codec.encode(new TransportMessage.Hello(
			"runelite",
			EventProtocol.NAME,
			EventProtocol.VERSION,
			capabilities
		));
		JsonObject root = new JsonParser().parse(json).getAsJsonObject();

		assertEquals(TransportProtocol.NAME, root.get("protocol").getAsString());
		assertEquals(TransportProtocol.VERSION, root.get("version").getAsInt());
		assertEquals("hello", root.get("kind").getAsString());
		JsonObject payload = root.getAsJsonObject("payload");
		assertEquals("runelite", payload.get("source").getAsString());
		JsonArray advertised = payload.getAsJsonArray("capabilities");
		assertEquals(3, advertised.size());
		assertEquals("experience", advertised.get(0).getAsString());
		assertEquals("resources", advertised.get(1).getAsString());
		assertEquals("actor.death", advertised.get(2).getAsString());
	}

	@Test
	public void roundTripsAllControlMessageKinds()
	{
		TransportMessage.Hello hello = (TransportMessage.Hello) codec.decode(codec.encode(
			new TransportMessage.Hello(
				"runelite",
				EventProtocol.NAME,
				EventProtocol.VERSION,
				capabilities
			)
		));
		assertEquals("runelite", hello.getSource());
		assertEquals(EventProtocol.NAME, hello.getEventProtocol());
		assertEquals(EventProtocol.VERSION, hello.getEventVersion());
		assertEquals(capabilities, hello.getCapabilities());

		TransportMessage.HelloAck ack = (TransportMessage.HelloAck) codec.decode(codec.encode(
			new TransportMessage.HelloAck(
				EventProtocol.NAME,
				EventProtocol.VERSION,
				capabilities
			)
		));
		assertEquals(EventProtocol.VERSION, ack.getEventVersion());
		assertEquals(capabilities, ack.getCapabilities());

		TransportMessage.Reset reset = (TransportMessage.Reset) codec.decode(codec.encode(
			new TransportMessage.Reset("runelite")
		));
		assertEquals("runelite", reset.getSource());

		TransportMessage.Error error = (TransportMessage.Error) codec.decode(codec.encode(
			new TransportMessage.Error("bad_thing", "Nope")
		));
		assertEquals("bad_thing", error.getCode());
		assertEquals("Nope", error.getMessage());
	}

	@Test
	public void eventAndStateAreDifferentWireKinds()
	{
		String eventJson = codec.encode(new TransportMessage.Event(
			new PlayerDeathEvent("runelite")
		));
		String stateJson = codec.encode(new TransportMessage.State(
			new VitalsChangedEvent(
				"runelite",
				VitalsChangedEvent.Kind.PRAYER,
				50,
				77
			)
		));

		assertEquals(
			"event",
			new JsonParser().parse(eventJson).getAsJsonObject().get("kind").getAsString()
		);
		assertEquals(
			"state",
			new JsonParser().parse(stateJson).getAsJsonObject().get("kind").getAsString()
		);

		TransportMessage.Event event = (TransportMessage.Event) codec.decode(eventJson);
		TransportMessage.State state = (TransportMessage.State) codec.decode(stateJson);
		assertTrue(event.getEvent() instanceof PlayerDeathEvent);
		assertTrue(state.getEvent() instanceof VitalsChangedEvent);
	}

	@Test(expected = TransportProtocolException.class)
	public void rejectsWrongTransportProtocol()
	{
		codec.decode("{\"protocol\":\"something-else\",\"version\":1,"
			+ "\"kind\":\"reset\",\"payload\":{\"source\":\"runelite\"}}");
	}

	@Test(expected = TransportProtocolException.class)
	public void rejectsFutureTransportVersion()
	{
		codec.decode("{\"protocol\":\"hapticscape-local-source\",\"version\":2,"
			+ "\"kind\":\"reset\",\"payload\":{\"source\":\"runelite\"}}");
	}

	@Test(expected = TransportProtocolException.class)
	public void rejectsUnknownMessageKind()
	{
		codec.decode("{\"protocol\":\"hapticscape-local-source\",\"version\":1,"
			+ "\"kind\":\"future_magic\",\"payload\":{}}");
	}

	@Test(expected = TransportProtocolException.class)
	public void rejectsUnknownCapability()
	{
		codec.decode("{\"protocol\":\"hapticscape-local-source\",\"version\":1,"
			+ "\"kind\":\"hello\",\"payload\":{\"source\":\"test\","
			+ "\"eventProtocol\":\"hapticscape-local-events\",\"eventVersion\":1,"
			+ "\"capabilities\":[\"future.magic\"]}}");
	}

	@Test(expected = TransportProtocolException.class)
	public void rejectsOversizedTransportMessage()
	{
		StringBuilder value = new StringBuilder(TransportProtocol.MAX_MESSAGE_CHARS + 1);
		for (int i = 0; i <= TransportProtocol.MAX_MESSAGE_CHARS; i++)
		{
			value.append('x');
		}
		codec.decode(value.toString());
	}
}
