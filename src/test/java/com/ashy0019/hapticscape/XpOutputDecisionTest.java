package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XpOutputDecisionTest
{
	@Test
	public void clickOnlySkillStillClicks()
	{
		XpOutputDecision decision = classify(changeBetweenXp(1_000, 1_050), false, true, 25, 25);

		assertEquals(XpFeedbackTrigger.NONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void hapticOnlySkillRetainsHapticFeedback()
	{
		XpOutputDecision decision = classify(changeBetweenXp(1_000, 1_050), true, false, 25, 25);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.NONE, decision.getClickTrigger());
		assertFalse(decision.shouldClick());
	}

	@Test
	public void bothEnabledProduceIndependentDecisions()
	{
		XpOutputDecision decision = classify(changeBetweenXp(1_000, 1_050), true, true, 25, 25);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void thresholdsAreIndependent()
	{
		XpOutputDecision decision = classify(changeBetweenXp(1_000, 1_050), true, true, 100, 25);

		assertEquals(XpFeedbackTrigger.NONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getClickTrigger());
	}

	@Test
	public void levelUpProducesOneSemanticClickDecision()
	{
		XpOutputDecision decision = classify(changeBetweenLevels(5, 6), true, true, 200_000_000, 200_000_000);

		assertEquals(XpFeedbackTrigger.LEVEL_UP, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.LEVEL_UP, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void milestoneProducesOneSemanticClickDecision()
	{
		XpOutputDecision decision = classify(changeBetweenLevels(9, 10), true, true, 1, 1);

		assertEquals(XpFeedbackTrigger.MILESTONE, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.MILESTONE, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void levelNinetyNineProducesOneClickBesideExistingCeremonyDecision()
	{
		XpOutputDecision decision = classify(changeBetweenLevels(98, 99), true, true, 1, 1);

		assertEquals(XpFeedbackTrigger.LEVEL_99, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.LEVEL_99, decision.getClickTrigger());
		assertTrue(decision.shouldClick());
	}

	@Test
	public void XPBelowClickThresholdDoesNotClick()
	{
		XpOutputDecision decision = classify(changeBetweenXp(1_000, 1_010), true, true, 1, 25);

		assertEquals(XpFeedbackTrigger.XP_GAIN, decision.getHapticTrigger());
		assertEquals(XpFeedbackTrigger.NONE, decision.getClickTrigger());
		assertFalse(decision.shouldClick());
	}

	private static XpOutputDecision classify(
		XpChange change,
		boolean hapticSkillEnabled,
		boolean clickSkillEnabled,
		int hapticThreshold,
		int clickThreshold)
	{
		return XpOutputDecision.classify(
			change,
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

	private static XpChange changeBetweenXp(int previousXp, int currentXp)
	{
		XpTracker tracker = new XpTracker();
		tracker.seed(Skill.AGILITY, previousXp);
		return tracker.update(Skill.AGILITY, currentXp);
	}

	private static XpChange changeBetweenLevels(int previousLevel, int currentLevel)
	{
		XpTracker tracker = new XpTracker();
		tracker.seed(Skill.AGILITY, Experience.getXpForLevel(previousLevel));
		return tracker.update(Skill.AGILITY, Experience.getXpForLevel(currentLevel));
	}
}
