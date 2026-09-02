package com.ashy0019.hapticscape;

import java.util.Objects;

public final class AlertProfile
{
	public static final int MINIMUM_THRESHOLD = 1;
	public static final int MAXIMUM_THRESHOLD = 200;

	private final AlertBehavior behavior;
	private final int intensityPercent;
	private final int durationMillis;
	private final HapticPatternSelection patternSelection;
	private final int threshold;

	public AlertProfile(
		AlertBehavior behavior,
		int intensityPercent,
		int durationMillis,
		HapticPatternSelection patternSelection,
		int threshold)
	{
		this.behavior = Objects.requireNonNull(behavior, "behavior");
		this.intensityPercent = requireRange(
			intensityPercent,
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			"intensity"
		);
		this.durationMillis = requireRange(
			durationMillis,
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			"duration"
		);
		this.patternSelection = Objects.requireNonNull(patternSelection, "patternSelection");
		this.threshold = requireRange(
			threshold,
			MINIMUM_THRESHOLD,
			MAXIMUM_THRESHOLD,
			"threshold"
		);
	}

	public AlertBehavior getBehavior()
	{
		return behavior;
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

	public int getThreshold()
	{
		return threshold;
	}

	AlertProfile withPattern(HapticPatternSelection updatedPattern)
	{
		return new AlertProfile(
			behavior,
			intensityPercent,
			durationMillis,
			updatedPattern,
			threshold
		);
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
