package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class CustomPattern
{
	public static final int EDITOR_SAMPLE_COUNT = 24;
	public static final int MINIMUM_SAMPLE_COUNT = 2;
	public static final int MAXIMUM_SAMPLE_COUNT = 24;
	private static final long TARGET_PLAYBACK_STEP_MILLIS = 50;

	private final int[] intensityPercentages;

	public CustomPattern(int... intensityPercentages)
	{
		Objects.requireNonNull(intensityPercentages, "intensityPercentages");
		if (intensityPercentages.length < MINIMUM_SAMPLE_COUNT
			|| intensityPercentages.length > MAXIMUM_SAMPLE_COUNT)
		{
			throw new IllegalArgumentException(
				"A custom pattern must contain between two and 24 samples"
			);
		}

		this.intensityPercentages = intensityPercentages.clone();
		for (int intensity : this.intensityPercentages)
		{
			if (intensity < 0 || intensity > 100)
			{
				throw new IllegalArgumentException(
					"Custom pattern intensity must be between 0 and 100"
				);
			}
		}
	}

	public static CustomPattern silent()
	{
		return new CustomPattern(0, 0);
	}

	public static CustomPattern fromConfigValue(String configuredValue)
	{
		Objects.requireNonNull(configuredValue, "configuredValue");
		String[] values = configuredValue.split(",", -1);
		if (values.length < MINIMUM_SAMPLE_COUNT || values.length > MAXIMUM_SAMPLE_COUNT)
		{
			throw new IllegalArgumentException("Invalid custom pattern sample count");
		}

		int[] intensities = new int[values.length];
		for (int index = 0; index < values.length; index++)
		{
			intensities[index] = Integer.parseInt(values[index].trim());
		}
		return new CustomPattern(intensities);
	}

	public int size()
	{
		return intensityPercentages.length;
	}

	public int getIntensityPercent(int sampleIndex)
	{
		return intensityPercentages[sampleIndex];
	}

	public double sampleAt(double normalizedTime)
	{
		double safeTime = Math.max(0.0, Math.min(1.0, normalizedTime));
		double position = safeTime * (intensityPercentages.length - 1);
		int left = (int) Math.floor(position);
		int right = Math.min(intensityPercentages.length - 1, left + 1);
		double fraction = position - left;
		return intensityPercentages[left]
			+ (intensityPercentages[right] - intensityPercentages[left]) * fraction;
	}

	public CustomPattern resampled(int sampleCount)
	{
		if (sampleCount < MINIMUM_SAMPLE_COUNT || sampleCount > MAXIMUM_SAMPLE_COUNT)
		{
			throw new IllegalArgumentException("Invalid resample count");
		}

		int[] resampled = new int[sampleCount];
		for (int index = 0; index < sampleCount; index++)
		{
			double position = (double) index / (sampleCount - 1);
			resampled[index] = (int) Math.round(sampleAt(position));
		}
		return new CustomPattern(resampled);
	}

	public HapticPattern createPattern(double maximumIntensity, Duration totalDuration)
	{
		Objects.requireNonNull(totalDuration, "totalDuration");
		double safeMaximum = Double.isFinite(maximumIntensity)
			? Math.max(0.0, Math.min(1.0, maximumIntensity))
			: 0.0;
		long totalMillis = Math.max(1, totalDuration.toMillis());
		int playbackSteps = (int) Math.max(
			1,
			Math.min(
				intensityPercentages.length,
				Math.max(1, totalMillis / TARGET_PLAYBACK_STEP_MILLIS)
			)
		);

		List<HapticPattern.Step> steps = new ArrayList<>(playbackSteps);
		long previousBoundary = 0;
		for (int index = 0; index < playbackSteps; index++)
		{
			long boundary = index == playbackSteps - 1
				? totalMillis
				: Math.round((double) totalMillis * (index + 1) / playbackSteps);
			long stepMillis = Math.max(1, boundary - previousBoundary);
			double samplePosition = playbackSteps == 1
				? 0.5
				: (double) index / (playbackSteps - 1);
			steps.add(new HapticPattern.Step(
				safeMaximum * sampleAt(samplePosition) / 100.0,
				Duration.ofMillis(stepMillis)
			));
			previousBoundary = boundary;
		}
		return new HapticPattern(steps);
	}

	public boolean isSilent()
	{
		for (int intensity : intensityPercentages)
		{
			if (intensity > 0)
			{
				return false;
			}
		}
		return true;
	}

	public String toConfigValue()
	{
		StringJoiner values = new StringJoiner(",");
		for (int intensity : intensityPercentages)
		{
			values.add(Integer.toString(intensity));
		}
		return values.toString();
	}

	@Override
	public boolean equals(Object other)
	{
		return this == other
			|| other instanceof CustomPattern
			&& Arrays.equals(
				intensityPercentages,
				((CustomPattern) other).intensityPercentages
			);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(intensityPercentages);
	}
}
