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
	private static final int PLAYER_INVENTORY_CAPACITY = 28;

	public Optional<InventoryOccupancyEvent> adapt(ItemContainerChanged event)
	{
		Objects.requireNonNull(event, "event");
		ItemContainer container = Objects.requireNonNull(event.getItemContainer(), "itemContainer");
		return adapt(event.getContainerId(), container.count());
	}

	Optional<InventoryOccupancyEvent> adapt(int containerId, int filledSlots)
	{
		if (containerId != InventoryID.INV)
		{
			return Optional.empty();
		}
		return Optional.of(inventory(filledSlots));
	}

	public InventoryOccupancyEvent inventory(ItemContainer container)
	{
		ItemContainer required = Objects.requireNonNull(container, "container");
		return inventory(required.count());
	}

	InventoryOccupancyEvent inventory(int filledSlots)
	{
		return new InventoryOccupancyEvent(
			SOURCE,
			filledSlots,
			PLAYER_INVENTORY_CAPACITY
		);
	}
}
