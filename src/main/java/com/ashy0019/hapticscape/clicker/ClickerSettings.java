package com.ashy0019.hapticscape.clicker;

/**
 * User-facing settings for the independent click output channel.
 */
public final class ClickerSettings
{
	public static final int MINIMUM_VOLUME_PERCENT = 0;
	public static final int MAXIMUM_VOLUME_PERCENT = 100;

	private final boolean enabled;
	private final int volumePercent;

	public ClickerSettings(boolean enabled, int volumePercent)
	{
		this.enabled = enabled;
		this.volumePercent = clamp(
			volumePercent,
			MINIMUM_VOLUME_PERCENT,
			MAXIMUM_VOLUME_PERCENT
		);
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public int getVolumePercent()
	{
		return volumePercent;
	}

	/**
	 * Converts the linear user-facing percentage to the decibel gain expected
	 * by RuneLite's AudioPlayer. Callers must treat zero percent as silence.
	 */
	public float getGainDb()
	{
		if (volumePercent <= 0)
		{
			throw new IllegalStateException("Zero percent volume is silent");
		}
		return (float) (20.0 * Math.log10(volumePercent / 100.0));
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
