package com.ashy0019.hapticscape;

import java.util.Objects;

public final class XpFeedbackSettings
{
	public static final int MINIMUM_XP_GAIN = 1;
	public static final int MAXIMUM_XP_GAIN = 200_000_000;
	public static final int MINIMUM_INTENSITY_PERCENT = 0;
	public static final int MAXIMUM_INTENSITY_PERCENT = 100;
	public static final int MINIMUM_DURATION_MILLIS = 50;
	public static final int MAXIMUM_DURATION_MILLIS = 10_000;

	private final int minimumXpGain;
	private final int intensityPercent;
	private final int durationMillis;
	private final HapticPatternSelection patternSelection;

	public XpFeedbackSettings(
		int minimumXpGain,
		int intensityPercent,
		int durationMillis,
		HapticPatternSelection patternSelection)
	{
		this.minimumXpGain = requireRange(
			minimumXpGain,
			MINIMUM_XP_GAIN,
			MAXIMUM_XP_GAIN,
			"minimum XP gain"
		);
		this.intensityPercent = requireRange(
			intensityPercent,
			MINIMUM_INTENSITY_PERCENT,
			MAXIMUM_INTENSITY_PERCENT,
			"intensity"
		);
		this.durationMillis = requireRange(
			durationMillis,
			MINIMUM_DURATION_MILLIS,
			MAXIMUM_DURATION_MILLIS,
			"duration"
		);
		this.patternSelection = Objects.requireNonNull(patternSelection, "patternSelection");
	}

	public int getMinimumXpGain()
	{
		return minimumXpGain;
	}

	public int getIntensityPercent()
	{
		return intensityPercent;
	}

	public int getDurationMillis()
	{
		return durationMillis;
	}

	public HapticPatternSelection getPatternSelection()
	{
		return patternSelection;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof XpFeedbackSettings))
		{
			return false;
		}

		XpFeedbackSettings that = (XpFeedbackSettings) other;
		return minimumXpGain == that.minimumXpGain
			&& intensityPercent == that.intensityPercent
			&& durationMillis == that.durationMillis
			&& patternSelection.equals(that.patternSelection);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(minimumXpGain, intensityPercent, durationMillis, patternSelection);
	}

	private static int requireRange(int value, int minimum, int maximum, String name)
	{
		if (value < minimum || value > maximum)
		{
			throw new IllegalArgumentException(
				name + " must be between " + minimum + " and " + maximum
			);
		}
		return value;
	}
}
