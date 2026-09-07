package com.ashy0019.hapticscape;

import static org.junit.Assert.assertEquals;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ResetAwareGameplayEventSinkTest
{
	@Test
	public void resetDelegatesAndRunsRuntimeResetHook()
	{
		RecordingSink delegate = new RecordingSink();
		AtomicInteger resets = new AtomicInteger();
		ResetAwareGameplayEventSink sink = new ResetAwareGameplayEventSink(
			delegate,
			resets::incrementAndGet
		);

		sink.resetSourceState();

		assertEquals(1, delegate.resets);
		assertEquals(1, resets.get());
	}

	private static final class RecordingSink implements GameplayEventSink
	{
		private int resets;

		@Override public void resetSourceState() { resets++; }
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
