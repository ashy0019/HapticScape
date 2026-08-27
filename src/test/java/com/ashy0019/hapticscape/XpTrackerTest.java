package com.ashy0019.hapticscape;

import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XpTrackerTest
{
	private final XpTracker tracker = new XpTracker();

	@Test
	public void firstObservationDoesNotProduceGain()
	{
		assertEquals(0, tracker.update(Skill.AGILITY, 1_000));
	}

	@Test
	public void laterObservationReturnsPositiveDifference()
	{
		tracker.seed(Skill.AGILITY, 1_000);

		assertEquals(75, tracker.update(Skill.AGILITY, 1_075));
	}

	@Test
	public void xpDecreaseDoesNotProduceGainAndBecomesNewBaseline()
	{
		tracker.seed(Skill.AGILITY, 1_000);

		assertEquals(0, tracker.update(Skill.AGILITY, 900));
		assertEquals(25, tracker.update(Skill.AGILITY, 925));
	}

	@Test
	public void skillsAreTrackedIndependently()
	{
		tracker.seed(Skill.AGILITY, 1_000);
		tracker.seed(Skill.COOKING, 2_000);

		assertEquals(10, tracker.update(Skill.AGILITY, 1_010));
		assertEquals(25, tracker.update(Skill.COOKING, 2_025));
	}

	@Test
	public void resetMakesNextObservationAnInitialization()
	{
		tracker.seed(Skill.AGILITY, 1_000);
		tracker.reset();

		assertEquals(0, tracker.update(Skill.AGILITY, 5_000));
	}
}
