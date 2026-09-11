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
	public void clampsMinimumXpGainAndRetainsBoundedSequences()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			0,
			ClickSequence.TWO,
			ClickSequence.THREE,
			ClickSequence.NONE
		);

		assertEquals(ClickerXpSettings.MINIMUM_XP_GAIN, settings.getMinimumXpGain());
		assertEquals(ClickSequence.TWO, settings.getXpGainSequence());
		assertEquals(ClickSequence.THREE, settings.getLevelUpOverride());
		assertEquals(ClickSequence.NONE, settings.getMilestoneOverride());
		assertTrue(settings.isLevelUpEnabled());
		assertFalse(settings.isMilestoneEnabled());
		assertFalse(settings.isLevel99Enabled());
	}

	@Test
	public void milestoneOverrideDoesNotDependOnOrdinaryLevelUpOverride()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			200_000_000,
			ClickSequence.ONE,
			ClickSequence.NONE,
			ClickSequence.THREE
		);

		XpEvent event = eventBetweenLevels(9, 10);
		assertEquals(XpFeedbackTrigger.MILESTONE, settings.classify(event));
		assertEquals(ClickSequence.THREE, settings.sequenceFor(event));
	}

	@Test
	public void missingSemanticOverrideFallsBackToOrdinaryXpRule()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			1,
			ClickSequence.TWO,
			ClickSequence.NONE,
			ClickSequence.NONE
		);

		XpEvent event = eventBetweenLevels(5, 6);
		assertEquals(XpFeedbackTrigger.XP_GAIN, settings.classify(event));
		assertEquals(ClickSequence.TWO, settings.sequenceFor(event));
	}

	@Test
	public void levelNinetyNineIsAlwaysSilentOnClickChannel()
	{
		ClickerXpSettings settings = new ClickerXpSettings(
			1,
			ClickSequence.THREE,
			ClickSequence.THREE,
			ClickSequence.THREE
		);

		XpEvent event = eventBetweenLevels(98, 99);
		assertEquals(XpFeedbackTrigger.NONE, settings.classify(event));
		assertEquals(ClickSequence.NONE, settings.sequenceFor(event));
	}

	private static XpEvent eventBetweenLevels(int previousLevel, int currentLevel)
	{
		return new XpEvent(
			"test-source",
			"agility",
			previousLevel * 1_000,
			currentLevel * 1_000,
			Math.max(0, (currentLevel - previousLevel) * 1_000),
			previousLevel,
			currentLevel
		);
	}
}
