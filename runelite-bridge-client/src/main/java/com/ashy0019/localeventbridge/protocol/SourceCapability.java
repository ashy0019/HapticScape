package com.ashy0019.localeventbridge.protocol;

import com.ashy0019.localeventbridge.event.ChatMessageEvent;
import com.ashy0019.localeventbridge.event.LocalEvent;
import com.ashy0019.localeventbridge.event.InventoryOccupancyEvent;
import com.ashy0019.localeventbridge.event.LootReceivedEvent;
import com.ashy0019.localeventbridge.event.NotificationEmittedEvent;
import com.ashy0019.localeventbridge.event.ActorDeathObservation;
import com.ashy0019.localeventbridge.event.StatusChangedEvent;
import com.ashy0019.localeventbridge.event.ResourceChangedEvent;
import com.ashy0019.localeventbridge.event.ExperienceChangedEvent;
import java.util.Locale;

/**
 * Stable capabilities advertised by a local event source during handshake.
 *
 * <p>Capabilities describe facts a source can publish; they do not grant a
 * local consumer any ability to control the source.</p>
 */
public enum SourceCapability
{
	EXPERIENCE("experience"),
	CHAT("chat"),
	RESOURCES("resources"),
	INVENTORY_OCCUPANCY("inventory.occupancy"),
	STATUS("status"),
	LOOT("loot"),
	ACTOR_DEATH("actor.death"),
	NOTIFICATION("notification");

	private final String wireName;

	SourceCapability(String wireName)
	{
		this.wireName = wireName;
	}

	public String getWireName()
	{
		return wireName;
	}

	public static SourceCapability fromWire(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			throw new TransportProtocolException("Source capability must not be empty");
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (SourceCapability capability : values())
		{
			if (capability.wireName.equals(normalized))
			{
				return capability;
			}
		}
		throw new TransportProtocolException("Unsupported source capability: " + value);
	}

	public static SourceCapability forEvent(LocalEvent event)
	{
		if (event instanceof ExperienceChangedEvent)
		{
			return EXPERIENCE;
		}
		if (event instanceof ChatMessageEvent)
		{
			return CHAT;
		}
		if (event instanceof ResourceChangedEvent)
		{
			return RESOURCES;
		}
		if (event instanceof InventoryOccupancyEvent)
		{
			return INVENTORY_OCCUPANCY;
		}
		if (event instanceof StatusChangedEvent)
		{
			return STATUS;
		}
		if (event instanceof LootReceivedEvent)
		{
			return LOOT;
		}
		if (event instanceof ActorDeathObservation)
		{
			return ACTOR_DEATH;
		}
		if (event instanceof NotificationEmittedEvent)
		{
			return NOTIFICATION;
		}
		throw new LocalEventProtocolException(
			"Event has no declared source capability: " + event.getClass().getName()
		);
	}
}
