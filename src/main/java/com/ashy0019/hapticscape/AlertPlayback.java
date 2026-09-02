package com.ashy0019.hapticscape;

import java.util.Objects;

public final class AlertPlayback
{
	private final HapticPatternSelection patternSelection;
	private final int intensityPercent;
	private final int durationMillis;

	public AlertPlayback(
		HapticPatternSelection patternSelection,
		int intensityPercent,
		int durationMillis)
	{
		this.patternSelection = Objects.requireNonNull(patternSelection, "patternSelection");
		this.intensityPercent = intensityPercent;
		this.durationMillis = durationMillis;
	}

	public HapticPatternSelection getPatternSelection()
	{
		return patternSelection;
	}

	public int getIntensityPercent()
	{
		return intensityPercent;
	}

	public int getDurationMillis()
	{
		return durationMillis;
	}
}
