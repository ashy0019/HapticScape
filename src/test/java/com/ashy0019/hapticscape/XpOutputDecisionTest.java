package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.event.XpEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XpOutputDecisionTest
{
	@Test
	public void clickOnlySkillStillClicksWithConfiguredSequence()
	{
		XpOutputDecision decision = classify(
			eventBetweenXp(1_000, 1_050),
			false,
			true,
			25,
			25,
			ClickSequence.TWO
		);

		assertEquals(XpFeedbackTrigger.NONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
		assertEquals(ClickSequence.TWO, decision.getClickSequence());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void hapticOnlySkillRetainsHapticFeedback()
	{
		XpOutputDecision decision = classify(
			eventBetweenXp(1_000, 1_050),
			true,
			false,
			25,
			25,
			ClickSequence.THREE
		);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.NONE, decision.getClickTrigger());
		assertEquals(ClickSequence.NONE, decision.getClickSequence());
		assertFalse(decision.shouldClick());
	}

	@Test
	public void thresholdsAreIndependent()
	{
		XpOutputDecision decision = classify(
			eventBetweenXp(1_000, 1_050),
			true,
			true,
			100,
			25,
			ClickSequence.THREE
		);

		assertEquals(XpFeedbackTrigger.NONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
		assertEquals(ClickSequence.THREE, decision.getClickSequence());
	}

	@Test
	public void levelUpAndMilestoneUseTheirOwnOverrides()
	{
		ClickerXpSettings clickSettings = new ClickerXpSettings(
			200_000_000,
			ClickSequence.ONE,
			ClickSequence.TWO,
			ClickSequence.THREE
		);
		XpOutputDecision level = classify(eventBetweenLevels(5, 6), clickSettings);
		XpOutputDecision milestone = classify(eventBetweenLevels(9, 10), clickSettings);

		assertEquals(XpFeedbackTrigger.LEVEL_UP, level.getClickTrigger());
		assertEquals(ClickSequence.TWO, level.getClickSequence());
		assertEquals(XpFeedbackTrigger.MILESTONE, milestone.getClickTrigger());
		assertEquals(ClickSequence.THREE, milestone.getClickSequence());
	}

	@Test
	public void levelNinetyNineNeverClicksBesideExistingCeremonyDecision()
	{
		ClickerXpSettings clickSettings = new ClickerXpSettings(
			1,
			ClickSequence.THREE,
			ClickSequence.THREE,
			ClickSequence.THREE
		);
		XpOutputDecision decision = classify(eventBetweenLevels(98, 99), clickSettings);

		assertEquals(XpFeedbackTrigger.LEVEL_99, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.NONE, decision.getClickTrigger());
		assertEquals(ClickSequence.NONE, decision.getClickSequence());
		assertFalse(decision.shouldClick());
	}

	private static XpOutputDecision classify(
		XpEvent event,
		boolean hapticSkillEnabled,
		boolean clickSkillEnabled,
		int hapticThreshold,
		int clickThreshold,
		ClickSequence xpSequence)
	{
		return XpOutputDecision.classify(
			event,
			hapticSkillEnabled,
			new XpFeedbackSettings(
				hapticThreshold,
				50,
				500,
				HapticPatternSelection.SINGLE
			),
			true,
			true,
			true,
			clickSkillEnabled,
			new ClickerXpSettings(
				clickThreshold,
				xpSequence,
				ClickSequence.ONE,
				ClickSequence.ONE
			)
		);
	}

	private static XpOutputDecision classify(XpEvent event, ClickerXpSettings clickSettings)
	{
		return XpOutputDecision.classify(
			event,
			true,
			new XpFeedbackSettings(
				1,
				50,
				500,
				HapticPatternSelection.SINGLE
			),
			true,
			true,
			true,
			true,
			clickSettings
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
