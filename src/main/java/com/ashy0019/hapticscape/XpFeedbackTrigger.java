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
		boolean milestoneFeedbackEnabled)
	{
		Objects.requireNonNull(change, "change");
		if (change.getGainedXp() <= 0)
		{
			return NONE;
		}

		if (levelUpFeedbackEnabled && change.isLevelUp())
		{
			if (milestoneFeedbackEnabled && change.crossedLevel(99))
			{
				return LEVEL_99;
			}
			if (milestoneFeedbackEnabled && change.crossedDecadeMilestone())
			{
				return MILESTONE;
			}
			return LEVEL_UP;
		}

		return change.getGainedXp() >= Math.max(1, minimumXpGain) ? XP_GAIN : NONE;
	}
}
