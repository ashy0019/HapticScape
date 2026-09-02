package com.ashy0019.hapticscape.music;

import java.util.Objects;

public final class MusicSyncSettings
{
	public static final int MINIMUM_SENSITIVITY_PERCENT = 25;
	public static final int MAXIMUM_SENSITIVITY_PERCENT = 200;
	public static final int MINIMUM_INTENSITY_PERCENT = 0;
	public static final int MAXIMUM_INTENSITY_PERCENT = 100;

	private final boolean enabled;
	private final MusicResponse response;
	private final int sensitivityPercent;
	private final int minimumIntensityPercent;
	private final int maximumIntensityPercent;

	public MusicSyncSettings(
		boolean enabled,
		MusicResponse response,
		int sensitivityPercent,
		int minimumIntensityPercent,
		int maximumIntensityPercent)
	{
		this.enabled = enabled;
		this.response = Objects.requireNonNull(response, "response");
		this.sensitivityPercent = requireRange(
			sensitivityPercent,
			MINIMUM_SENSITIVITY_PERCENT,
			MAXIMUM_SENSITIVITY_PERCENT,
			"sensitivity"
		);
		this.minimumIntensityPercent = requireRange(
			minimumIntensityPercent,
			MINIMUM_INTENSITY_PERCENT,
			MAXIMUM_INTENSITY_PERCENT,
			"minimum intensity"
		);
		this.maximumIntensityPercent = requireRange(
			maximumIntensityPercent,
			MINIMUM_INTENSITY_PERCENT,
			MAXIMUM_INTENSITY_PERCENT,
			"maximum intensity"
		);
		if (minimumIntensityPercent > maximumIntensityPercent)
		{
			throw new IllegalArgumentException(
				"Minimum intensity cannot exceed maximum intensity"
			);
		}
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public MusicResponse getResponse()
	{
		return response;
	}

	public int getSensitivityPercent()
	{
		return sensitivityPercent;
	}

	public int getMinimumIntensityPercent()
	{
		return minimumIntensityPercent;
	}

	public int getMaximumIntensityPercent()
	{
		return maximumIntensityPercent;
	}

	public double mapIntensity(double analyzedLevel)
	{
		double sensitiveLevel = clamp(
			analyzedLevel * sensitivityPercent / 100.0
		);
		if (sensitiveLevel < 0.01 || maximumIntensityPercent == 0)
		{
			return 0.0;
		}

		double minimum = minimumIntensityPercent / 100.0;
		double maximum = maximumIntensityPercent / 100.0;
		return minimum + (maximum - minimum) * sensitiveLevel;
	}

	public MusicSyncSettings withEnabled(boolean nextEnabled)
	{
		return new MusicSyncSettings(
			nextEnabled,
			response,
			sensitivityPercent,
			minimumIntensityPercent,
			maximumIntensityPercent
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

	private static double clamp(double value)
	{
		if (!Double.isFinite(value))
		{
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
