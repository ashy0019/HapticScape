package com.ashy0019.hapticscape;

import java.util.Objects;

public final class AlertProfile
{
	private final AlertBehavior behavior;
	private final int intensityPercent;
	private final int durationMillis;
	private final HapticPatternSelection patternSelection;

	public AlertProfile(
		AlertBehavior behavior,
		int intensityPercent,
		int durationMillis,
		HapticPatternSelection patternSelection)
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

	AlertProfile withPattern(HapticPatternSelection updatedPattern)
	{
		return new AlertProfile(
			behavior,
			intensityPercent,
			durationMillis,
			updatedPattern
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
