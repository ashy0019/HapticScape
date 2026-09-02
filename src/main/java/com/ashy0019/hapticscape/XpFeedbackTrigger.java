package com.ashy0019.hapticscape;

import java.util.Objects;

public enum XpFeedbackTrigger
{
	NONE,
	XP_GAIN,
	LEVEL_UP,
	MILESTONE,
	LEVEL_99;

	public static XpFeedbackTrigger classify(
		XpChange change,
		int minimumXpGain,
		boolean levelUpFeedbackEnabled,
		boolean milestoneFeedbackEnabled,
		boolean level99CelebrationEnabled)
	{
		Objects.requireNonNull(change, "change");
		if (change.getGainedXp() <= 0)
		{
			return NONE;
		}

		if (change.isLevelUp())
		{
			if (level99CelebrationEnabled && change.crossedLevel(99))
			{
				return LEVEL_99;
			}
			if (levelUpFeedbackEnabled)
			{
				if (milestoneFeedbackEnabled && change.crossedDecadeMilestone())
				{
					return MILESTONE;
				}
				return LEVEL_UP;
			}
		}

		return change.getGainedXp() >= Math.max(1, minimumXpGain) ? XP_GAIN : NONE;
	}
}
