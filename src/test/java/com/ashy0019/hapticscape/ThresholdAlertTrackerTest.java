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

	@Test
	public void specialAttackFiresWhenCrossingUpwardAndRearmsBelowThreshold()
	{
		ThresholdAlertTracker tracker = new ThresholdAlertTracker();
		tracker.seed(AlertCategory.SPECIAL_ATTACK_READY, 60);

		assertFalse(tracker.update(AlertCategory.SPECIAL_ATTACK_READY, 70, 75));
		assertTrue(tracker.update(AlertCategory.SPECIAL_ATTACK_READY, 75, 75));
		assertFalse(tracker.update(AlertCategory.SPECIAL_ATTACK_READY, 100, 75));
		assertFalse(tracker.update(AlertCategory.SPECIAL_ATTACK_READY, 50, 75));
		assertTrue(tracker.update(AlertCategory.SPECIAL_ATTACK_READY, 80, 75));
	}

	@Test(expected = IllegalArgumentException.class)
	public void nonCrossingCategoryCannotUseThresholdTracker()
	{
		new ThresholdAlertTracker().seed(AlertCategory.VALUABLE_DROP, 100_000);
	}
}
