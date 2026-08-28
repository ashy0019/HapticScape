package com.ashy0019.hapticscape;

import java.util.Objects;

public final class NotificationFeedbackSettings
{
	public static final int MINIMUM_INTENSITY_PERCENT = 0;
	public static final int MAXIMUM_INTENSITY_PERCENT = 100;
	public static final int MINIMUM_DURATION_MILLIS = 50;
	public static final int MAXIMUM_DURATION_MILLIS = 10_000;

	private final boolean enabled;
	private final boolean respectRuneLiteFocus;
	private final int intensityPercent;
	private final int durationMillis;
	private final HapticPatternSelection patternSelection;

	public NotificationFeedbackSettings(
		boolean enabled,
		boolean respectRuneLiteFocus,
		int intensityPercent,
		int durationMillis,
		HapticPatternSelection patternSelection)
	{
		this.enabled = enabled;
		this.respectRuneLiteFocus = respectRuneLiteFocus;
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

	public boolean isEnabled()
	{
		return enabled;
	}

	public boolean isRespectRuneLiteFocus()
	{
		return respectRuneLiteFocus;
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

	public boolean shouldPlay(boolean runeLiteFocused, boolean notificationSendsWhenFocused)
	{
		if (!enabled)
		{
			return false;
		}

		return !respectRuneLiteFocus || !runeLiteFocused || notificationSendsWhenFocused;
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
