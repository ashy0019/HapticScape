package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public enum HapticPatternSelection
{
	SINGLE("Single pulse", HapticPatternPreset.SINGLE, null),
	DOUBLE("Double pulse", HapticPatternPreset.DOUBLE, null),
	TRIPLE("Triple pulse", HapticPatternPreset.TRIPLE, null),
	ASCENDING("Ascending", HapticPatternPreset.ASCENDING, null),
	CUSTOM_I(null, null, CustomPatternSlot.I),
	CUSTOM_II(null, null, CustomPatternSlot.II),
	CUSTOM_III(null, null, CustomPatternSlot.III),
	CUSTOM_IV(null, null, CustomPatternSlot.IV);

	private final String displayName;
	private final HapticPatternPreset preset;
	private final CustomPatternSlot customSlot;

	HapticPatternSelection(
		String displayName,
		HapticPatternPreset preset,
		CustomPatternSlot customSlot)
	{
		this.displayName = displayName;
		this.preset = preset;
		this.customSlot = customSlot;
	}

	public static HapticPatternSelection fromConfigValue(String value)
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

	public HapticPattern createPattern(
		CustomPatternLibrary customPatterns,
		double maximumIntensity,
		Duration totalDuration)
	{
		if (preset != null)
		{
			return preset.createPattern(maximumIntensity, totalDuration);
		}

		return Objects.requireNonNull(customPatterns, "customPatterns")
			.get(customSlot)
			.createPattern(maximumIntensity, totalDuration);
	}

	public String getDisplayName(CustomPatternLibrary customPatterns)
	{
		return customSlot == null
			? displayName
			: Objects.requireNonNull(customPatterns, "customPatterns").getName(customSlot);
	}

	public boolean isCustom()
	{
		return customSlot != null;
	}

	@Override
	public String toString()
	{
		return customSlot == null ? displayName : customSlot.getDefaultName();
	}
}
