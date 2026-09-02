package com.ashy0019.hapticscape.music;

public enum MusicResponse
{
	SMOOTH("Smooth", 0.85, 0.15, 0.30, 0.07),
	RHYTHMIC("Rhythmic", 0.50, 0.50, 0.55, 0.13),
	PUNCHY("Punchy", 0.20, 0.80, 0.82, 0.24);

	private final String displayName;
	private final double bassWeight;
	private final double onsetWeight;
	private final double attack;
	private final double release;

	MusicResponse(
		String displayName,
		double bassWeight,
		double onsetWeight,
		double attack,
		double release)
	{
		this.displayName = displayName;
		this.bassWeight = bassWeight;
		this.onsetWeight = onsetWeight;
		this.attack = attack;
		this.release = release;
	}

	double combine(double bass, double onset)
	{
		return clamp(bass * bassWeight + onset * onsetWeight);
	}

	double smooth(double previous, double target)
	{
		double coefficient = target >= previous ? attack : release;
		return clamp(previous + (target - previous) * coefficient);
	}

	@Override
	public String toString()
	{
		return displayName;
	}

	private static double clamp(double value)
	{
		return Math.max(0.0, Math.min(1.0, value));
	}
}
