package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TransportWireCodecTest
{
	private final TransportWireCodec codec = new TransportWireCodec(new Gson());

	@Test
	public void writesStableTransportEnvelope()
	{
		String json = codec.encode(new TransportMessage.Hello(
			"runelite",
			EventProtocol.NAME,
			EventProtocol.VERSION
		));
		JsonObject root = new JsonParser().parse(json).getAsJsonObject();

		assertEquals(TransportProtocol.NAME, root.get("protocol").getAsString());
		assertEquals(TransportProtocol.VERSION, root.get("version").getAsInt());
		assertEquals("hello", root.get("kind").getAsString());
		assertEquals("runelite", root.getAsJsonObject("payload").get("source").getAsString());
	}

	@Test
	public void roundTripsAllControlMessageKinds()
	{
		TransportMessage.Hello hello = (TransportMessage.Hello) codec.decode(codec.encode(
			new TransportMessage.Hello("runelite", EventProtocol.NAME, EventProtocol.VERSION)
		));
		assertEquals("runelite", hello.getSource());
		assertEquals(EventProtocol.NAME, hello.getEventProtocol());
		assertEquals(EventProtocol.VERSION, hello.getEventVersion());

		TransportMessage.HelloAck ack = (TransportMessage.HelloAck) codec.decode(codec.encode(
			new TransportMessage.HelloAck(EventProtocol.NAME, EventProtocol.VERSION)
		));
		assertEquals(EventProtocol.VERSION, ack.getEventVersion());

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
	public void roundTripsPublishedAndSeededEvents()
	{
		TransportMessage.Event published = (TransportMessage.Event) codec.decode(codec.encode(
			new TransportMessage.Event(
				TransportMessage.EventOperation.PUBLISH,
				new PlayerDeathEvent("runelite")
			)
		));
		assertEquals(TransportMessage.EventOperation.PUBLISH, published.getOperation());
		assertTrue(published.getEvent() instanceof PlayerDeathEvent);

		TransportMessage.Event seeded = (TransportMessage.Event) codec.decode(codec.encode(
			new TransportMessage.Event(
				TransportMessage.EventOperation.SEED,
				new VitalsChangedEvent(
					"runelite",
					VitalsChangedEvent.Kind.PRAYER,
					50,
					77
				)
			)
		));
		assertEquals(TransportMessage.EventOperation.SEED, seeded.getOperation());
		assertTrue(seeded.getEvent() instanceof VitalsChangedEvent);
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
		codec.decode("{\"protocol\":\"hapticscape-transport\",\"version\":2,"
			+ "\"kind\":\"reset\",\"payload\":{\"source\":\"runelite\"}}");
	}

	@Test(expected = TransportProtocolException.class)
	public void rejectsUnknownMessageKind()
	{
		codec.decode("{\"protocol\":\"hapticscape-transport\",\"version\":1,"
			+ "\"kind\":\"future_magic\",\"payload\":{}}");
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
