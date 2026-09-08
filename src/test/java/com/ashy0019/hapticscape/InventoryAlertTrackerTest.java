package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventoryAlertTrackerTest
{
	@Test
	public void firesOnlyWhenInventoryBecomesFull()
	{
		InventoryAlertTracker tracker = new InventoryAlertTracker();
		tracker.seed(event("test-source", 27, 28));

		assertTrue(tracker.update(event("test-source", 28, 28)).isPresent());
		assertFalse(tracker.update(event("test-source", 28, 28)).isPresent());
		assertFalse(tracker.update(event("test-source", 27, 28)).isPresent());
		assertTrue(tracker.update(event("test-source", 28, 28)).isPresent());
	}

	@Test
	public void firstObservationDoesNotFireAndSourcesAreIndependent()
	{
		InventoryAlertTracker tracker = new InventoryAlertTracker();

		assertFalse(tracker.update(event("test-source", 28, 28)).isPresent());
		tracker.seed(event("other-game", 9, 10));
		assertTrue(tracker.update(event("other-game", 10, 10)).isPresent());
		assertFalse(tracker.update(event("test-source", 28, 28)).isPresent());
	}

	@Test
	public void zeroCapacityIsNeverFull()
	{
		InventoryAlertTracker tracker = new InventoryAlertTracker();
		tracker.seed(event("test-source", 0, 0));

		assertFalse(tracker.update(event("test-source", 0, 0)).isPresent());
	}

	private static InventoryChangedEvent event(String source, int filledSlots, int capacity)
	{
		return new InventoryChangedEvent(source, filledSlots, capacity);
	}
}
