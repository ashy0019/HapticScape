package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Objects;

public enum XpFeedbackTrigger
{
	NONE,
	XP_GAIN,
	LEVEL_UP,
	MILESTONE,
	LEVEL_99;

	public static XpFeedbackTrigger classify(
		XpEvent event,
		int minimumXpGain,
		boolean levelUpFeedbackEnabled,
		boolean milestoneFeedbackEnabled,
		boolean level99CelebrationEnabled)
	{
		Objects.requireNonNull(event, "event");
		return classifyValues(
			event.getGainedXp(),
			event.isLevelUp(),
			event.crossedLevel(99),
			event.crossedDecadeMilestone(),
			minimumXpGain,
			levelUpFeedbackEnabled,
			milestoneFeedbackEnabled,
			level99CelebrationEnabled
		);
	}

	/** Temporary compatibility overload for pre-event-boundary callers and tests. */
	@Deprecated
	public static XpFeedbackTrigger classify(
		XpChange change,
		int minimumXpGain,
		boolean levelUpFeedbackEnabled,
		boolean milestoneFeedbackEnabled,
		boolean level99CelebrationEnabled)
	{
		Objects.requireNonNull(change, "change");
		return classifyValues(
			change.getGainedXp(),
			change.isLevelUp(),
			change.crossedLevel(99),
			change.crossedDecadeMilestone(),
			minimumXpGain,
			levelUpFeedbackEnabled,
			milestoneFeedbackEnabled,
			level99CelebrationEnabled
		);
	}

	private static XpFeedbackTrigger classifyValues(
		int gainedXp,
		boolean levelUp,
		boolean crossedLevel99,
		boolean crossedDecadeMilestone,
		int minimumXpGain,
		boolean levelUpFeedbackEnabled,
		boolean milestoneFeedbackEnabled,
		boolean level99CelebrationEnabled)
	{
		if (gainedXp <= 0)
		{
			return NONE;
		}

		if (levelUp)
		{
			if (level99CelebrationEnabled && crossedLevel99)
			{
				return LEVEL_99;
			}
			if (levelUpFeedbackEnabled)
			{
				if (milestoneFeedbackEnabled && crossedDecadeMilestone)
				{
					return MILESTONE;
				}
				return LEVEL_UP;
			}
		}

		return gainedXp >= Math.max(1, minimumXpGain) ? XP_GAIN : NONE;
	}
}
