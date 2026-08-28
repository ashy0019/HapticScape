package com.ashy0019.hapticscape;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XpFeedbackTriggerTest
{
	@Test
	public void ordinaryXpGainUsesXpTriggerWhenThresholdIsMet()
	{
		XpChange change = changeBetweenXp(1_000, 1_050);

		assertEquals(
			XpFeedbackTrigger.XP_GAIN,
			XpFeedbackTrigger.classify(change, 25, true, true)
		);
	}

	@Test
	public void ordinaryXpGainBelowThresholdIsIgnored()
	{
		XpChange change = changeBetweenXp(1_000, 1_010);

		assertEquals(
			XpFeedbackTrigger.NONE,
			XpFeedbackTrigger.classify(change, 25, true, true)
		);
	}

	@Test
	public void levelUpTakesPriorityOverXpThreshold()
	{
		XpChange change = changeBetweenLevels(5, 6);

		assertEquals(
			XpFeedbackTrigger.LEVEL_UP,
			XpFeedbackTrigger.classify(change, 200_000_000, true, true)
		);
	}

	@Test
	public void decadeMilestoneTakesPriorityOverOrdinaryLevelUp()
	{
		XpChange change = changeBetweenLevels(9, 10);

		assertEquals(
			XpFeedbackTrigger.MILESTONE,
			XpFeedbackTrigger.classify(change, 1, true, true)
		);
	}

	@Test
	public void levelNinetyNineTakesHighestPriority()
	{
		XpChange change = changeBetweenLevels(89, 99);

		assertEquals(
			XpFeedbackTrigger.LEVEL_99,
			XpFeedbackTrigger.classify(change, 1, true, true)
		);
	}

	@Test
	public void disablingMilestonesFallsBackToOrdinaryLevelUp()
	{
		XpChange change = changeBetweenLevels(9, 10);

		assertEquals(
			XpFeedbackTrigger.LEVEL_UP,
			XpFeedbackTrigger.classify(change, 1, true, false)
		);
	}

	@Test
	public void disablingLevelUpFeedbackFallsBackToQualifiedXp()
	{
		XpChange change = changeBetweenLevels(9, 10);

		assertEquals(
			XpFeedbackTrigger.XP_GAIN,
			XpFeedbackTrigger.classify(change, 1, false, true)
		);
	}

	private static XpChange changeBetweenXp(int previousXp, int currentXp)
	{
		XpTracker tracker = new XpTracker();
		tracker.seed(Skill.AGILITY, previousXp);
		return tracker.update(Skill.AGILITY, currentXp);
	}

	private static XpChange changeBetweenLevels(int previousLevel, int currentLevel)
	{
		return changeBetweenXp(
			Experience.getXpForLevel(previousLevel),
			Experience.getXpForLevel(currentLevel)
		);
	}
}
