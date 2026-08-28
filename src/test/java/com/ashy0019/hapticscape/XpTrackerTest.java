package com.ashy0019.hapticscape;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XpTrackerTest
{
	private final XpTracker tracker = new XpTracker();

	@Test
	public void firstObservationDoesNotProduceGain()
	{
		assertEquals(0, tracker.update(Skill.AGILITY, 1_000).getGainedXp());
	}

	@Test
	public void laterObservationReturnsPositiveDifference()
	{
		tracker.seed(Skill.AGILITY, 1_000);

		assertEquals(75, tracker.update(Skill.AGILITY, 1_075).getGainedXp());
	}

	@Test
	public void xpDecreaseDoesNotProduceGainAndBecomesNewBaseline()
	{
		tracker.seed(Skill.AGILITY, 1_000);

		assertEquals(0, tracker.update(Skill.AGILITY, 900).getGainedXp());
		assertEquals(25, tracker.update(Skill.AGILITY, 925).getGainedXp());
	}

	@Test
	public void skillsAreTrackedIndependently()
	{
		tracker.seed(Skill.AGILITY, 1_000);
		tracker.seed(Skill.COOKING, 2_000);

		assertEquals(10, tracker.update(Skill.AGILITY, 1_010).getGainedXp());
		assertEquals(25, tracker.update(Skill.COOKING, 2_025).getGainedXp());
	}

	@Test
	public void resetMakesNextObservationAnInitialization()
	{
		tracker.seed(Skill.AGILITY, 1_000);
		tracker.reset();

		assertEquals(0, tracker.update(Skill.AGILITY, 5_000).getGainedXp());
	}

	@Test
	public void crossingXpThresholdProducesRealLevelUp()
	{
		tracker.seed(Skill.AGILITY, Experience.getXpForLevel(10) - 1);

		XpChange change = tracker.update(Skill.AGILITY, Experience.getXpForLevel(10));

		assertTrue(change.isLevelUp());
		assertEquals(9, change.getPreviousLevel());
		assertEquals(10, change.getCurrentLevel());
	}

	@Test
	public void jumpingMultipleLevelsStillDetectsCrossedMilestone()
	{
		tracker.seed(Skill.AGILITY, Experience.getXpForLevel(9));

		XpChange change = tracker.update(Skill.AGILITY, Experience.getXpForLevel(11));

		assertTrue(change.crossedDecadeMilestone());
	}

	@Test
	public void virtualLevelsAboveNinetyNineAreNotRealLevelUps()
	{
		tracker.seed(Skill.AGILITY, Experience.getXpForLevel(99));

		XpChange change = tracker.update(Skill.AGILITY, Experience.getXpForLevel(100));

		assertFalse(change.isLevelUp());
		assertEquals(99, change.getPreviousLevel());
		assertEquals(99, change.getCurrentLevel());
	}
}
