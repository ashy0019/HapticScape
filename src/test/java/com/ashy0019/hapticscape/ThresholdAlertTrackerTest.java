package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThresholdAlertTrackerTest
{
	@Test
	public void firesOnlyWhenCrossingDownwardThroughThreshold()
	{
		ThresholdAlertTracker tracker = new ThresholdAlertTracker();
		tracker.seed(AlertCategory.LOW_HITPOINTS, 40);

		assertFalse(tracker.update(AlertCategory.LOW_HITPOINTS, 30, 20));
		assertTrue(tracker.update(AlertCategory.LOW_HITPOINTS, 20, 20));
		assertFalse(tracker.update(AlertCategory.LOW_HITPOINTS, 15, 20));
	}

	@Test
	public void rearmsAfterRecoveringAboveThreshold()
	{
		ThresholdAlertTracker tracker = new ThresholdAlertTracker();
		tracker.seed(AlertCategory.LOW_PRAYER, 15);

		assertTrue(tracker.update(AlertCategory.LOW_PRAYER, 10, 10));
		assertFalse(tracker.update(AlertCategory.LOW_PRAYER, 9, 10));
		assertFalse(tracker.update(AlertCategory.LOW_PRAYER, 20, 10));
		assertTrue(tracker.update(AlertCategory.LOW_PRAYER, 10, 10));
	}
}
