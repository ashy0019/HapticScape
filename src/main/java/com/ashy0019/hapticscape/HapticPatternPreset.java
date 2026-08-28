package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum HapticPatternPreset
{
	SINGLE("Single pulse", new double[]{1.0}, new int[]{1}),
	DOUBLE("Double pulse", new double[]{1.0, 0.0, 1.0}, new int[]{2, 1, 2}),
	TRIPLE("Triple pulse", new double[]{1.0, 0.0, 1.0, 0.0, 1.0}, new int[]{2, 1, 2, 1, 2}),
	ASCENDING("Ascending", new double[]{0.35, 0.0, 0.65, 0.0, 1.0}, new int[]{2, 1, 2, 1, 2});

	private final String displayName;
	private final double[] intensityFactors;
	private final int[] durationWeights;

	HapticPatternPreset(String displayName, double[] intensityFactors, int[] durationWeights)
	{
		this.displayName = displayName;
		this.intensityFactors = intensityFactors.clone();
		this.durationWeights = durationWeights.clone();
	}

	public HapticPattern createPattern(double maximumIntensity, Duration totalDuration)
	{
		Objects.requireNonNull(totalDuration, "totalDuration");
		double safeMaximum = Double.isFinite(maximumIntensity)
			? Math.max(0.0, Math.min(1.0, maximumIntensity))
			: 0.0;
		long totalMillis = Math.max(intensityFactors.length, totalDuration.toMillis());
		int totalWeight = 0;
		for (int weight : durationWeights)
		{
			totalWeight += weight;
		}

		List<HapticPattern.Step> steps = new ArrayList<>(intensityFactors.length);
		int cumulativeWeight = 0;
		long previousBoundary = 0;
		for (int index = 0; index < intensityFactors.length; index++)
		{
			cumulativeWeight += durationWeights[index];
			long boundary = index == intensityFactors.length - 1
				? totalMillis
				: Math.round((double) totalMillis * cumulativeWeight / totalWeight);
			long stepMillis = boundary - previousBoundary;
			steps.add(new HapticPattern.Step(
				safeMaximum * intensityFactors[index],
				Duration.ofMillis(stepMillis)
			));
			previousBoundary = boundary;
		}
		return new HapticPattern(steps);
	}

	public static HapticPatternPreset fromConfigValue(String value)
	{
		if (value == null)
		{
			return SINGLE;
		}

		try
		{
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e)
		{
			return SINGLE;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
