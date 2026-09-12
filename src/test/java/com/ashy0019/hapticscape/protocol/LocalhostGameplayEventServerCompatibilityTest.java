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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocalhostGameplayEventServerCompatibilityTest
{
	@Test
	public void legacyHelloReceivesLegacyCompatibleAcknowledgement() throws Exception
	{
		TransportWireCodec codec = new TransportWireCodec(new Gson());
		try (LocalhostGameplayEventServer server =
			new LocalhostGameplayEventServer(codec, new NoOpSink(), 0);
			Socket socket = new Socket())
		{
			socket.connect(
				new InetSocketAddress(LocalhostTransportEndpoint.address(), server.getPort()),
				2_000
			);
			socket.setSoTimeout(2_000);
			String hello = "{\"protocol\":\"hapticscape-local-source\",\"version\":1,"
				+ "\"kind\":\"hello\",\"payload\":{\"source\":\"legacy-source\","
				+ "\"eventProtocol\":\"hapticscape-local-events\",\"eventVersion\":1,"
				+ "\"capabilities\":[\"actor.death\"]}}";

			LocalhostFrameIo.write(socket.getOutputStream(), hello);
			String response = LocalhostFrameIo.read(socket.getInputStream());
			JsonObject root = new JsonParser().parse(response).getAsJsonObject();

			assertEquals(TransportProtocol.LEGACY_NAME, root.get("protocol").getAsString());
			assertEquals("hello_ack", root.get("kind").getAsString());
			assertEquals(
				EventProtocol.LEGACY_NAME,
				root.getAsJsonObject("payload").get("eventProtocol").getAsString()
			);
		}
	}

	private static final class NoOpSink implements GameplayEventSink
	{
		@Override public void resetSourceState() { }
		@Override public void seedVitals(VitalsChangedEvent event) { }
		@Override public void seedInventory(InventoryChangedEvent event) { }
		@Override public void seedToxicStatus(ToxicStatusChangedEvent event) { }
		@Override public void onXpEvent(XpEvent event) { }
		@Override public void onChatEvent(ChatEvent event) { }
		@Override public void onVitalsEvent(VitalsChangedEvent event) { }
		@Override public void onInventoryEvent(InventoryChangedEvent event) { }
		@Override public void onToxicStatusEvent(ToxicStatusChangedEvent event) { }
		@Override public void onLootEvent(LootReceivedEvent event) { }
		@Override public void onPlayerDeathEvent(PlayerDeathEvent event) { }
		@Override public void onNotificationEvent(NotificationEvent event) { }
	}
}
