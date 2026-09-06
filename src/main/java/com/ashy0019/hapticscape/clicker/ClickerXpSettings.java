package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.XpChange;
import com.ashy0019.hapticscape.XpFeedbackTrigger;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Objects;

/**
 * Determines which skill XP changes qualify for one auditory click.
 */
public final class ClickerXpSettings
{
	public static final int MINIMUM_XP_GAIN = 1;
	public static final int MAXIMUM_XP_GAIN = 200_000_000;

	private final int minimumXpGain;
	private final boolean levelUpEnabled;
	private final boolean milestoneEnabled;
	private final boolean level99Enabled;

	public ClickerXpSettings(
		int minimumXpGain,
		boolean levelUpEnabled,
		boolean milestoneEnabled,
		boolean level99Enabled)
	{
		this.minimumXpGain = clamp(
			minimumXpGain,
			MINIMUM_XP_GAIN,
			MAXIMUM_XP_GAIN
		);
		this.levelUpEnabled = levelUpEnabled;
		this.milestoneEnabled = milestoneEnabled;
		this.level99Enabled = level99Enabled;
	}

	public int getMinimumXpGain()
	{
		return minimumXpGain;
	}

	public boolean isLevelUpEnabled()
	{
		return levelUpEnabled;
	}

	public boolean isMilestoneEnabled()
	{
		return milestoneEnabled;
	}

	public boolean isLevel99Enabled()
	{
		return level99Enabled;
	}

	public XpFeedbackTrigger classify(XpEvent event)
	{
		Objects.requireNonNull(event, "event");
		return classifyValues(
			event.getGainedXp(),
			event.isLevelUp(),
			event.crossedLevel(99),
			event.crossedDecadeMilestone()
		);
	}

	/** Temporary compatibility overload for pre-event-boundary callers and tests. */
	@Deprecated
	public XpFeedbackTrigger classify(XpChange change)
	{
		Objects.requireNonNull(change, "change");
		return classifyValues(
			change.getGainedXp(),
			change.isLevelUp(),
			change.crossedLevel(99),
			change.crossedDecadeMilestone()
		);
	}

	private XpFeedbackTrigger classifyValues(
		int gainedXp,
		boolean levelUp,
		boolean crossedLevel99,
		boolean crossedDecadeMilestone)
	{
		if (gainedXp <= 0)
		{
			return XpFeedbackTrigger.NONE;
		}
		if (levelUp)
		{
			if (level99Enabled && crossedLevel99)
			{
				return XpFeedbackTrigger.LEVEL_99;
			}
			if (milestoneEnabled && crossedDecadeMilestone)
			{
				return XpFeedbackTrigger.MILESTONE;
			}
			if (levelUpEnabled)
			{
				return XpFeedbackTrigger.LEVEL_UP;
			}
		}
		return gainedXp >= minimumXpGain
			? XpFeedbackTrigger.XP_GAIN
			: XpFeedbackTrigger.NONE;
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
