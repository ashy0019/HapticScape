package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import java.util.Objects;

/**
 * Independently classifies one XP change for the haptic and click channels.
 */
public final class XpOutputDecision
{
	private final XpFeedbackTrigger hapticTrigger;
	private final XpFeedbackTrigger clickTrigger;

	private XpOutputDecision(
		XpFeedbackTrigger hapticTrigger,
		XpFeedbackTrigger clickTrigger)
	{
		this.hapticTrigger = Objects.requireNonNull(hapticTrigger);
		this.clickTrigger = Objects.requireNonNull(clickTrigger);
	}

	public static XpOutputDecision classify(
		XpChange change,
		boolean hapticSkillEnabled,
		XpFeedbackSettings hapticSettings,
		boolean hapticLevelUpEnabled,
		boolean hapticMilestoneEnabled,
		boolean hapticLevel99Enabled,
		boolean clickSkillEnabled,
		ClickerXpSettings clickSettings)
	{
		Objects.requireNonNull(change, "change");
		Objects.requireNonNull(hapticSettings, "hapticSettings");
		Objects.requireNonNull(clickSettings, "clickSettings");

		XpFeedbackTrigger haptic = hapticSkillEnabled
			? XpFeedbackTrigger.classify(
				change,
				hapticSettings.getMinimumXpGain(),
				hapticLevelUpEnabled,
				hapticMilestoneEnabled,
				hapticLevel99Enabled
			)
			: XpFeedbackTrigger.NONE;
		XpFeedbackTrigger click = clickSkillEnabled
			? clickSettings.classify(change)
			: XpFeedbackTrigger.NONE;

		return new XpOutputDecision(haptic, click);
	}

	public XpFeedbackTrigger getHapticTrigger()
	{
		return hapticTrigger;
	}

	public XpFeedbackTrigger getClickTrigger()
	{
		return clickTrigger;
	}

	public boolean shouldClick()
	{
		return clickTrigger != XpFeedbackTrigger.NONE;
	}
}
