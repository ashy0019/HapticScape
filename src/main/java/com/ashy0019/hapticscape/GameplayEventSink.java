package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;

/**
 * Source-neutral ingress for gameplay observations. Integrations translate
 * host/game-specific events before they cross this boundary.
 */
public interface GameplayEventSink
{
	void resetSourceState();

	void seedVitals(VitalsChangedEvent event);

	void seedInventory(InventoryChangedEvent event);

	void seedToxicStatus(ToxicStatusChangedEvent event);

	void onXpEvent(XpEvent event);

	void onChatEvent(ChatEvent event);

	void onVitalsEvent(VitalsChangedEvent event);

	void onInventoryEvent(InventoryChangedEvent event);

	void onToxicStatusEvent(ToxicStatusChangedEvent event);

	void onLootEvent(LootReceivedEvent event);

	void onPlayerDeathEvent(PlayerDeathEvent event);

	void onNotificationEvent(NotificationEvent event);
}
