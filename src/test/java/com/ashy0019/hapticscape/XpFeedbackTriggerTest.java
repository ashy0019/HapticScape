package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.XpEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XpFeedbackTriggerTest
{
	@Test
	public void ordinaryXpGainUsesXpTriggerWhenThresholdIsMet()
	{
		XpEvent event = eventBetweenXp(1_000, 1_050);

		assertEquals(
			XpFeedbackTrigger.XP_GAIN,
			XpFeedbackTrigger.classify(event, 25, true, true, true)
		);
	}

	@Test
	public void ordinaryXpGainBelowThresholdIsIgnored()
	{
		XpEvent event = eventBetweenXp(1_000, 1_010);

		assertEquals(
			XpFeedbackTrigger.NONE,
			XpFeedbackTrigger.classify(event, 25, true, true, true)
		);
	}

	@Test
	public void levelUpTakesPriorityOverXpThreshold()
	{
		XpEvent event = eventBetweenLevels(5, 6);

		assertEquals(
			XpFeedbackTrigger.LEVEL_UP,
			XpFeedbackTrigger.classify(event, 200_000_000, true, true, true)
		);
	}

	@Test
	public void decadeMilestoneTakesPriorityOverOrdinaryLevelUp()
	{
		XpEvent event = eventBetweenLevels(9, 10);

		assertEquals(
			XpFeedbackTrigger.MILESTONE,
			XpFeedbackTrigger.classify(event, 1, true, true, true)
		);
	}

	@Test
	public void levelNinetyNineTakesHighestPriority()
	{
		XpEvent event = eventBetweenLevels(89, 99);

		assertEquals(
			XpFeedbackTrigger.LEVEL_99,
			XpFeedbackTrigger.classify(event, 1, true, true, true)
		);
	}

	@Test
	public void levelNinetyNineDoesNotDependOnOrdinaryLevelUpOrMilestoneSettings()
	{
		XpEvent event = eventBetweenLevels(98, 99);

		assertEquals(
			XpFeedbackTrigger.LEVEL_99,
			XpFeedbackTrigger.classify(event, 200_000_000, false, false, true)
		);
	}

	@Test
	public void levelNinetyNineTriggerRetainsTheSkillThatActuallyReachedNinetyNine()
	{
		XpEvent event = eventBetweenLevels("cooking", 98, 99);

		assertEquals("cooking", event.getSkillId());
		assertEquals(
			XpFeedbackTrigger.LEVEL_99,
			XpFeedbackTrigger.classify(event, 1, true, true, true)
		);
	}

	@Test
	public void disablingLevelNinetyNineFallsBackToOrdinaryLevelUp()
	{
		XpEvent event = eventBetweenLevels(98, 99);

		assertEquals(
			XpFeedbackTrigger.LEVEL_UP,
			XpFeedbackTrigger.classify(event, 200_000_000, true, true, false)
		);
	}

	@Test
	public void disablingLevelNinetyNineAndLevelUpsFallsBackToQualifiedXp()
	{
		XpEvent event = eventBetweenLevels(98, 99);

		assertEquals(
			XpFeedbackTrigger.XP_GAIN,
			XpFeedbackTrigger.classify(event, 1, false, true, false)
		);
	}

	@Test
	public void disablingMilestonesFallsBackToOrdinaryLevelUp()
	{
		XpEvent event = eventBetweenLevels(9, 10);

		assertEquals(
			XpFeedbackTrigger.LEVEL_UP,
			XpFeedbackTrigger.classify(event, 1, true, false, true)
		);
	}

	@Test
	public void disablingLevelUpFeedbackFallsBackToQualifiedXp()
	{
		XpEvent event = eventBetweenLevels(9, 10);

		assertEquals(
			XpFeedbackTrigger.XP_GAIN,
			XpFeedbackTrigger.classify(event, 1, false, true, false)
		);
	}

	private static XpEvent eventBetweenXp(int previousXp, int currentXp)
	{
		return new XpEvent(
			"test-source",
			"agility",
			previousXp,
			currentXp,
			Math.max(0, currentXp - previousXp),
			1,
			1
		);
	}

	private static XpEvent eventBetweenLevels(int previousLevel, int currentLevel)
	{
		return eventBetweenLevels("agility", previousLevel, currentLevel);
	}

	private static XpEvent eventBetweenLevels(
		String skillId,
		int previousLevel,
		int currentLevel)
	{
		return new XpEvent(
			"test-source",
			skillId,
			previousLevel * 1_000,
			currentLevel * 1_000,
			Math.max(0, (currentLevel - previousLevel) * 1_000),
			previousLevel,
			currentLevel
		);
	}
}
