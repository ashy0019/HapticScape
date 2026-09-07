package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;

/** Translates RuneLite player-inventory occupancy into a neutral event. */
public final class RuneLiteInventoryEventAdapter
{
	private static final String SOURCE = "runelite";

	public Optional<InventoryChangedEvent> adapt(ItemContainerChanged event)
	{
		Objects.requireNonNull(event, "event");
		ItemContainer container = Objects.requireNonNull(event.getItemContainer(), "itemContainer");
		return adapt(event.getContainerId(), container.count(), container.size());
	}

	Optional<InventoryChangedEvent> adapt(int containerId, int filledSlots, int capacity)
	{
		if (containerId != InventoryID.INV)
		{
			return Optional.empty();
		}
		return Optional.of(inventory(filledSlots, capacity));
	}

	public InventoryChangedEvent inventory(ItemContainer container)
	{
		ItemContainer required = Objects.requireNonNull(container, "container");
		return inventory(required.count(), required.size());
	}

	InventoryChangedEvent inventory(int filledSlots, int capacity)
	{
		return new InventoryChangedEvent(SOURCE, filledSlots, capacity);
	}
}
