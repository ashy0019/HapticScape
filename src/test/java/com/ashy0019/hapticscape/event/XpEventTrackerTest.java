package com.ashy0019.hapticscape.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XpEventTrackerTest
{
	private final XpEventTracker tracker = new XpEventTracker();

	@Test
	public void firstObservationDoesNotProduceGain()
	{
		assertEquals(0, tracker.update("test", "agility", 1_000, 9).getGainedXp());
	}

	@Test
	public void laterObservationReturnsPositiveDifference()
	{
		tracker.seed("test", "agility", 1_000, 9);

		assertEquals(75, tracker.update("test", "agility", 1_075, 9).getGainedXp());
	}

	@Test
	public void xpDecreaseDoesNotProduceGainAndBecomesNewBaseline()
	{
		tracker.seed("test", "agility", 1_000, 9);

		assertEquals(0, tracker.update("test", "agility", 900, 8).getGainedXp());
		assertEquals(25, tracker.update("test", "agility", 925, 8).getGainedXp());
	}

	@Test
	public void skillsAreTrackedIndependently()
	{
		tracker.seed("test", "agility", 1_000, 9);
		tracker.seed("test", "cooking", 2_000, 13);

		assertEquals(10, tracker.update("test", "agility", 1_010, 9).getGainedXp());
		assertEquals(25, tracker.update("test", "cooking", 2_025, 13).getGainedXp());
	}

	@Test
	public void sourcesAreTrackedIndependently()
	{
		tracker.seed("source-a", "agility", 1_000, 9);
		tracker.seed("source-b", "agility", 5_000, 20);

		assertEquals(10, tracker.update("source-a", "agility", 1_010, 9).getGainedXp());
		assertEquals(50, tracker.update("source-b", "agility", 5_050, 20).getGainedXp());
	}

	@Test
	public void resetMakesNextObservationAnInitialization()
	{
		tracker.seed("test", "agility", 1_000, 9);
		tracker.reset();

		assertEquals(0, tracker.update("test", "agility", 5_000, 20).getGainedXp());
	}

	@Test
	public void suppliedLevelChangeProducesLevelUp()
	{
		tracker.seed("test", "agility", 999, 9);

		XpEvent event = tracker.update("test", "agility", 1_000, 10);

		assertTrue(event.isLevelUp());
		assertEquals(9, event.getPreviousLevel());
		assertEquals(10, event.getCurrentLevel());
	}

	@Test
	public void jumpingMultipleLevelsStillDetectsCrossedMilestone()
	{
		tracker.seed("test", "agility", 900, 9);
		XpEvent event = tracker.update("test", "agility", 1_100, 11);

		assertTrue(event.crossedDecadeMilestone());
	}

	@Test
	public void levelNinetyNineCrossingOnlyOccursOnceWhenSourceLevelStaysCapped()
	{
		tracker.seed("test", "agility", 9_800, 98);

		XpEvent levelNinetyNine = tracker.update("test", "agility", 9_900, 99);
		XpEvent laterXp = tracker.update("test", "agility", 10_900, 99);

		assertTrue(levelNinetyNine.crossedLevel(99));
		assertFalse(laterXp.crossedLevel(99));
	}
}
