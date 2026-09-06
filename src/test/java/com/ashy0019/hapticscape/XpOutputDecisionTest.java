package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.event.XpEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XpOutputDecisionTest
{
	@Test
	public void clickOnlySkillStillClicks()
	{
		XpOutputDecision decision = classify(eventBetweenXp(1_000, 1_050), false, true, 25, 25);

		assertEquals(XpFeedbackTrigger.NONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void hapticOnlySkillRetainsHapticFeedback()
	{
		XpOutputDecision decision = classify(eventBetweenXp(1_000, 1_050), true, false, 25, 25);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.NONE, decision.getClickTrigger());
		assertFalse(decision.shouldClick());
	}

	@Test
	public void bothEnabledProduceIndependentDecisions()
	{
		XpOutputDecision decision = classify(eventBetweenXp(1_000, 1_050), true, true, 25, 25);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void thresholdsAreIndependent()
	{
		XpOutputDecision decision = classify(eventBetweenXp(1_000, 1_050), true, true, 100, 25);

		assertEquals(XpFeedbackTrigger.NONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
	}

	@Test
	public void levelUpProducesOneSemanticClickDecision()
	{
		XpOutputDecision decision = classify(eventBetweenLevels(5, 6), true, true, 200_000_000, 200_000_000);

		assertEquals(XpFeedbackTrigger.LEVEL_UP, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.LEVEL_UP, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void milestoneProducesOneSemanticClickDecision()
	{
		XpOutputDecision decision = classify(eventBetweenLevels(9, 10), true, true, 1, 1);

		assertEquals(XpFeedbackTrigger.MILESTONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.MILESTONE, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void levelNinetyNineProducesOneClickBesideExistingCeremonyDecision()
	{
		XpOutputDecision decision = classify(eventBetweenLevels(98, 99), true, true, 1, 1);

		assertEquals(XpFeedbackTrigger.LEVEL_99, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.LEVEL_99, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void XPBelowClickThresholdDoesNotClick()
	{
		XpOutputDecision decision = classify(eventBetweenXp(1_000, 1_010), true, true, 1, 25);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.NONE, decision.getClickTrigger());
		assertFalse(decision.shouldClick());
	}

	private static XpOutputDecision classify(
		XpEvent event,
		boolean hapticSkillEnabled,
		boolean clickSkillEnabled,
		int hapticThreshold,
		int clickThreshold)
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
			new ClickerXpSettings(clickThreshold, true, true, true)
		);
	}

	private static XpEvent eventBetweenXp(int previousXp, int currentXp)
	{
		return new XpEvent(
			XpEvent.SOURCE_RUNELITE,
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
