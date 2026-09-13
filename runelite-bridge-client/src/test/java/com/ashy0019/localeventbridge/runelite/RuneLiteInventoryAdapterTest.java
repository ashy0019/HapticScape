package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.InventoryOccupancyEvent;
import java.util.Optional;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteInventoryAdapterTest
{
	private final RuneLiteInventoryAdapter adapter = new RuneLiteInventoryAdapter();

	@Test
	public void mapsPlayerInventoryOccupancyToNeutralState()
	{
		InventoryOccupancyEvent event = adapter.adapt(InventoryID.INV, 17).get();

		assertEquals("runelite", event.getSource());
		assertEquals(InventoryOccupancyEvent.TYPE, event.getType());
		assertEquals(17, event.getFilledSlots());
		assertEquals(28, event.getCapacity());
		assertFalse(event.isFull());
	}

	@Test
	public void mapsFullInventoryAndIgnoresOtherContainers()
	{
		InventoryOccupancyEvent full = adapter.adapt(InventoryID.INV, 28).get();
		Optional<InventoryOccupancyEvent> other = adapter.adapt(InventoryID.INV + 1, 28);

		assertTrue(full.isFull());
		assertEquals(28, full.getCapacity());
		assertFalse(other.isPresent());
	}
}
