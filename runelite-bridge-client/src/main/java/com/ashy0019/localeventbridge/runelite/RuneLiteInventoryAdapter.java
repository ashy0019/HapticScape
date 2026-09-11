package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.InventoryOccupancyEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;

/** Translates RuneLite player-inventory occupancy into a neutral event. */
public final class RuneLiteInventoryAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;

	public Optional<InventoryOccupancyEvent> adapt(ItemContainerChanged event)
	{
		Objects.requireNonNull(event, "event");
		ItemContainer container = Objects.requireNonNull(event.getItemContainer(), "itemContainer");
		return adapt(event.getContainerId(), container.count(), container.size());
	}

	Optional<InventoryOccupancyEvent> adapt(int containerId, int filledSlots, int capacity)
	{
		if (containerId != InventoryID.INV)
		{
			return Optional.empty();
		}
		return Optional.of(inventory(filledSlots, capacity));
	}

	public InventoryOccupancyEvent inventory(ItemContainer container)
	{
		ItemContainer required = Objects.requireNonNull(container, "container");
		return inventory(required.count(), required.size());
	}

	InventoryOccupancyEvent inventory(int filledSlots, int capacity)
	{
		return new InventoryOccupancyEvent(SOURCE, filledSlots, capacity);
	}
}
