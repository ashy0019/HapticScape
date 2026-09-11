package com.ashy0019.localeventbridge;

import com.ashy0019.localeventbridge.event.ActorDeathObservation;
import com.ashy0019.localeventbridge.event.ChatMessageEvent;
import com.ashy0019.localeventbridge.event.ExperienceChangedEvent;
import com.ashy0019.localeventbridge.event.InventoryOccupancyEvent;
import com.ashy0019.localeventbridge.event.LootReceivedEvent;
import com.ashy0019.localeventbridge.event.NotificationEmittedEvent;
import com.ashy0019.localeventbridge.event.ResourceChangedEvent;
import com.ashy0019.localeventbridge.event.StatusChangedEvent;

/**
 * Source-neutral publication boundary used by RuneLite adapters.
 *
 * <p>Implementations may publish events anywhere, but RuneLite-specific
 * objects must not cross this interface.</p>
 */
public interface LocalEventSink
{
	void resetSourceState();

	void seedResource(ResourceChangedEvent event);

	void seedInventory(InventoryOccupancyEvent event);

	void seedStatus(StatusChangedEvent event);

	void onExperience(ExperienceChangedEvent event);

	void onChat(ChatMessageEvent event);

	void onResource(ResourceChangedEvent event);

	void onInventory(InventoryOccupancyEvent event);

	void onStatus(StatusChangedEvent event);

	void onLoot(LootReceivedEvent event);

	void onActorDeath(ActorDeathObservation event);

	void onNotification(NotificationEmittedEvent event);
}
