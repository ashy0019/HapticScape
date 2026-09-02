package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.XpChange;
import com.ashy0019.hapticscape.XpFeedbackTrigger;
import com.ashy0019.hapticscape.XpTracker;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
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
			settings.classify(changeBetweenLevels(9, 10))
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
			settings.classify(changeBetweenLevels(98, 99))
		);
	}

	private static XpChange changeBetweenLevels(int previousLevel, int currentLevel)
	{
		XpTracker tracker = new XpTracker();
		tracker.seed(Skill.AGILITY, Experience.getXpForLevel(previousLevel));
		return tracker.update(Skill.AGILITY, Experience.getXpForLevel(currentLevel));
	}
}
