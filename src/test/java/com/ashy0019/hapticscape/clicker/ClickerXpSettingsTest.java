package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.XpFeedbackTrigger;
import com.ashy0019.hapticscape.event.XpEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickerXpSettingsTest
{
	@Test
	public void clampsMinimumXpGainAndRetainsSemanticChoices()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			0,
			true,
			false,
			true
		);

		assertEquals(ClickerXpSettings.MINIMUM_XP_GAIN, settings.getMinimumXpGain());
		assertTrue(settings.isLevelUpEnabled());
		assertFalse(settings.isMilestoneEnabled());
		assertTrue(settings.isLevel99Enabled());
	}

	@Test
	public void milestoneChoiceDoesNotDependOnOrdinaryLevelUpChoice()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			200_000_000,
			false,
			true,
			false
		);

		assertEquals(
			XpFeedbackTrigger.MILESTONE,
			settings.classify(eventBetweenLevels(9, 10))
		);
	}

	@Test
	public void disabledSemanticChoicesFallBackToXpThreshold()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			200_000_000,
			false,
			false,
			false
		);

		assertEquals(
			XpFeedbackTrigger.NONE,
			settings.classify(eventBetweenLevels(98, 99))
		);
	}

	private static XpEvent eventBetweenLevels(int previousLevel, int currentLevel)
	{
		return new XpEvent(
			XpEvent.SOURCE_RUNELITE,
			"agility",
			previousLevel * 1_000,
			currentLevel * 1_000,
			Math.max(0, (currentLevel - previousLevel) * 1_000),
			previousLevel,
			currentLevel
		);
	}
}
