package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.HapticScapeEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Locale;

/**
 * Stable capabilities advertised by a local event source during handshake.
 *
 * <p>Capabilities describe facts a source can publish; they do not grant the
 * HapticScape application any ability to control the source.</p>
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

	public static SourceCapability forEvent(HapticScapeEvent event)
	{
		if (event instanceof XpEvent)
		{
			return EXPERIENCE;
		}
		if (event instanceof ChatEvent)
		{
			return CHAT;
		}
		if (event instanceof VitalsChangedEvent)
		{
			return RESOURCES;
		}
		if (event instanceof InventoryChangedEvent)
		{
			return INVENTORY_OCCUPANCY;
		}
		if (event instanceof ToxicStatusChangedEvent)
		{
			return STATUS;
		}
		if (event instanceof LootReceivedEvent)
		{
			return LOOT;
		}
		if (event instanceof PlayerDeathEvent)
		{
			return ACTOR_DEATH;
		}
		if (event instanceof NotificationEvent)
		{
			return NOTIFICATION;
		}
		throw new EventProtocolException(
			"Event has no declared source capability: " + event.getClass().getName()
		);
	}
}
