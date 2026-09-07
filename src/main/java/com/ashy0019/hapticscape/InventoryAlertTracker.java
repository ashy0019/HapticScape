package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Source-neutral transition policy for inventory-full alerts. */
public final class InventoryAlertTracker
{
	private final Map<String, Boolean> previousFull = new HashMap<>();

	public void seed(InventoryChangedEvent event)
	{
		InventoryChangedEvent required = Objects.requireNonNull(event, "event");
		previousFull.put(required.getSource(), required.isFull());
	}

	public Optional<AlertCategory> update(InventoryChangedEvent event)
	{
		InventoryChangedEvent required = Objects.requireNonNull(event, "event");
		boolean currentFull = required.isFull();
		Boolean previous = previousFull.put(required.getSource(), currentFull);
		if (previous != null && !previous && currentFull)
		{
			return Optional.of(AlertCategory.INVENTORY_FULL);
		}
		return Optional.empty();
	}

	public void reset()
	{
		previousFull.clear();
	}
}
