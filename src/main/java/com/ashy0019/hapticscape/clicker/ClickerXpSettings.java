package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.XpFeedbackTrigger;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Objects;

/**
 * Click policy for skill XP observations.
 *
 * <p>The ordinary XP sequence is the baseline. Level-up and milestone
 * sequences are optional overrides; {@link ClickSequence#NONE} means
 * "inherit the ordinary XP rule" for those overrides. Level 99 is
 * intentionally silent on the click channel because it has its own ceremony.</p>
 */
public final class ClickerXpSettings
{
	public static final int MINIMUM_XP_GAIN = 1;
	public static final int MAXIMUM_XP_GAIN = 200_000_000;

	private final int minimumXpGain;
	private final ClickSequence xpGainSequence;
	private final ClickSequence levelUpOverride;
	private final ClickSequence milestoneOverride;

	public ClickerXpSettings(
		int minimumXpGain,
		ClickSequence xpGainSequence,
		ClickSequence levelUpOverride,
		ClickSequence milestoneOverride)
	{
		this.minimumXpGain = clamp(
			minimumXpGain,
			MINIMUM_XP_GAIN,
			MAXIMUM_XP_GAIN
		);
		ClickSequence ordinary = Objects.requireNonNull(xpGainSequence, "xpGainSequence");
		this.xpGainSequence = ordinary.isEnabled() ? ordinary : ClickSequence.ONE;
		this.levelUpOverride = Objects.requireNonNull(levelUpOverride, "levelUpOverride");
		this.milestoneOverride = Objects.requireNonNull(milestoneOverride, "milestoneOverride");
	}

	/**
	 * Compatibility constructor for the pre-sequence UI. Enabled overrides
	 * become one click; the old level-99 flag is deliberately ignored.
	 */
	public ClickerXpSettings(
		int minimumXpGain,
		boolean levelUpEnabled,
		boolean milestoneEnabled,
		boolean level99Enabled)
	{
		this(
			minimumXpGain,
			ClickSequence.ONE,
			levelUpEnabled ? ClickSequence.ONE : ClickSequence.NONE,
			milestoneEnabled ? ClickSequence.ONE : ClickSequence.NONE
		);
	}

	public int getMinimumXpGain()
	{
		return minimumXpGain;
	}

	public ClickSequence getXpGainSequence()
	{
		return xpGainSequence;
	}

	public ClickSequence getLevelUpOverride()
	{
		return levelUpOverride;
	}

	public ClickSequence getMilestoneOverride()
	{
		return milestoneOverride;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof ClickerXpSettings))
		{
			return false;
		}
		ClickerXpSettings that = (ClickerXpSettings) other;
		return minimumXpGain == that.minimumXpGain
			&& xpGainSequence == that.xpGainSequence
			&& levelUpOverride == that.levelUpOverride
			&& milestoneOverride == that.milestoneOverride;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(
			minimumXpGain,
			xpGainSequence,
			levelUpOverride,
			milestoneOverride
		);
	}

	/** Compatibility view for the current checkbox UI. */
	public boolean isLevelUpEnabled()
	{
		return levelUpOverride.isEnabled();
	}

	/** Compatibility view for the current checkbox UI. */
	public boolean isMilestoneEnabled()
	{
		return milestoneOverride.isEnabled();
	}

	/** Level 99 never emits click feedback. */
	public boolean isLevel99Enabled()
	{
		return false;
	}

	public XpFeedbackTrigger classify(XpEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (event.getGainedXp() <= 0 || event.crossedLevel(99))
		{
			return XpFeedbackTrigger.NONE;
		}
		if (event.isLevelUp())
		{
			if (event.crossedDecadeMilestone() && milestoneOverride.isEnabled())
			{
				return XpFeedbackTrigger.MILESTONE;
			}
			if (levelUpOverride.isEnabled())
			{
				return XpFeedbackTrigger.LEVEL_UP;
			}
		}
		return event.getGainedXp() >= minimumXpGain && xpGainSequence.isEnabled()
			? XpFeedbackTrigger.XP_GAIN
			: XpFeedbackTrigger.NONE;
	}

	public ClickSequence sequenceFor(XpEvent event)
	{
		switch (classify(event))
		{
			case LEVEL_UP:
				return levelUpOverride;
			case MILESTONE:
				return milestoneOverride;
			case XP_GAIN:
				return xpGainSequence;
			case NONE:
			case LEVEL_99:
			default:
				return ClickSequence.NONE;
		}
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
