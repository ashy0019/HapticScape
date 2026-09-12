package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Objects;

/**
 * Independently classifies one XP change for the haptic and click channels.
 */
public final class XpOutputDecision
{
	private final XpFeedbackTrigger hapticTrigger;
	private final XpFeedbackTrigger clickTrigger;
	private final ClickSequence clickSequence;

	private XpOutputDecision(
		XpFeedbackTrigger hapticTrigger,
		XpFeedbackTrigger clickTrigger,
		ClickSequence clickSequence)
	{
		this.hapticTrigger = Objects.requireNonNull(hapticTrigger);
		this.clickTrigger = Objects.requireNonNull(clickTrigger);
		this.clickSequence = Objects.requireNonNull(clickSequence);
	}

	public static XpOutputDecision classify(
		XpEvent event,
		boolean hapticSkillEnabled,
		XpFeedbackSettings hapticSettings,
		boolean hapticLevelUpEnabled,
		boolean hapticMilestoneEnabled,
		boolean hapticLevel99Enabled,
		boolean clickSkillEnabled,
		ClickerXpSettings clickSettings)
	{
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(hapticSettings, "hapticSettings");
		Objects.requireNonNull(clickSettings, "clickSettings");

		XpFeedbackTrigger haptic = hapticSkillEnabled
			? XpFeedbackTrigger.classify(
				event,
				hapticSettings.getMinimumXpGain(),
				hapticLevelUpEnabled,
				hapticMilestoneEnabled,
				hapticLevel99Enabled
			)
			: XpFeedbackTrigger.NONE;
		XpFeedbackTrigger click = clickSkillEnabled
			? clickSettings.classify(event)
			: XpFeedbackTrigger.NONE;
		ClickSequence sequence = clickSkillEnabled
			? clickSettings.sequenceFor(event)
			: ClickSequence.NONE;

		return new XpOutputDecision(haptic, click, sequence);
	}

	public XpFeedbackTrigger getHapticTrigger()
	{
		return hapticTrigger;
	}

	public XpFeedbackTrigger getClickTrigger()
	{
		return clickTrigger;
	}

	public ClickSequence getClickSequence()
	{
		return clickSequence;
	}

	public boolean shouldClick()
	{
		return clickSequence.isEnabled();
	}
}
