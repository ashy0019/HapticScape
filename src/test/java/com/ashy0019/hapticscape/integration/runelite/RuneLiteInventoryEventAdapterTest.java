package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import java.util.Optional;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteInventoryEventAdapterTest
{
	private final RuneLiteInventoryEventAdapter adapter = new RuneLiteInventoryEventAdapter();

	@Test
	public void mapsPlayerInventoryOccupancyToNeutralState()
	{
		InventoryChangedEvent event = adapter.adapt(InventoryID.INV, 27, 28).get();

		assertEquals("runelite", event.getSource());
		assertEquals(InventoryChangedEvent.TYPE, event.getType());
		assertEquals(27, event.getFilledSlots());
		assertEquals(28, event.getCapacity());
		assertFalse(event.isFull());
	}

	@Test
	public void mapsFullInventoryAndIgnoresOtherContainers()
	{
		InventoryChangedEvent full = adapter.adapt(InventoryID.INV, 28, 28).get();
		Optional<InventoryChangedEvent> other = adapter.adapt(InventoryID.INV + 1, 28, 28);

		assertTrue(full.isFull());
		assertFalse(other.isPresent());
	}
}
