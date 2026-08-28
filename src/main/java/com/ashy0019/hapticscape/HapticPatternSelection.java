package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A built-in preset or a reference to a dynamically created custom pattern.
 */
public final class HapticPatternSelection
{
	public static final HapticPatternSelection SINGLE =
		builtIn("SINGLE", "Single pulse", HapticPatternPreset.SINGLE);
	public static final HapticPatternSelection DOUBLE =
		builtIn("DOUBLE", "Double pulse", HapticPatternPreset.DOUBLE);
	public static final HapticPatternSelection TRIPLE =
		builtIn("TRIPLE", "Triple pulse", HapticPatternPreset.TRIPLE);
	public static final HapticPatternSelection ASCENDING =
		builtIn("ASCENDING", "Ascending", HapticPatternPreset.ASCENDING);

	private static final String CUSTOM_PREFIX = "CUSTOM:";
	private static final List<HapticPatternSelection> BUILT_INS;

	static
	{
		List<HapticPatternSelection> builtIns = new ArrayList<>();
		builtIns.add(SINGLE);
		builtIns.add(DOUBLE);
		builtIns.add(TRIPLE);
		builtIns.add(ASCENDING);
		BUILT_INS = Collections.unmodifiableList(builtIns);
	}

	private final String configValue;
	private final String displayName;
	private final HapticPatternPreset preset;
	private final Integer customPatternId;

	private HapticPatternSelection(
		String configValue,
		String displayName,
		HapticPatternPreset preset,
		Integer customPatternId)
	{
		this.configValue = configValue;
		this.displayName = displayName;
		this.preset = preset;
		this.customPatternId = customPatternId;
	}

	public static HapticPatternSelection custom(int patternId)
	{
		if (patternId <= 0)
		{
			throw new IllegalArgumentException("A custom pattern id must be positive");
		}
		return new HapticPatternSelection(
			CUSTOM_PREFIX + patternId,
			null,
			null,
			patternId
		);
	}

	public static HapticPatternSelection fromConfigValue(String value)
	{
		if (value == null)
		{
			return SINGLE;
		}

		String normalized = value.trim().toUpperCase(Locale.ROOT);
		for (HapticPatternSelection selection : BUILT_INS)
		{
			if (selection.configValue.equals(normalized))
			{
				return selection;
			}
		}

		int legacyId = legacyCustomId(normalized);
		if (legacyId > 0)
		{
			return custom(legacyId);
		}

		if (normalized.startsWith(CUSTOM_PREFIX))
		{
			try
			{
				return custom(Integer.parseInt(normalized.substring(CUSTOM_PREFIX.length())));
			}
			catch (IllegalArgumentException ignored)
			{
				return SINGLE;
			}
		}
		return SINGLE;
	}

	public static List<HapticPatternSelection> availableSelections(
		CustomPatternLibrary customPatterns)
	{
		List<HapticPatternSelection> selections = new ArrayList<>(BUILT_INS);
		for (CustomPatternEntry entry : Objects.requireNonNull(
			customPatterns,
			"customPatterns"
		).getPatterns())
		{
			selections.add(custom(entry.getId()));
		}
		return selections;
	}

	public HapticPatternSelection resolveAgainst(CustomPatternLibrary customPatterns)
	{
		return customPatternId == null
			|| Objects.requireNonNull(customPatterns, "customPatterns").contains(customPatternId)
			? this
			: SINGLE;
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

		CustomPatternEntry entry = Objects.requireNonNull(
			customPatterns,
			"customPatterns"
		).findById(customPatternId).orElse(null);
		return entry == null
			? HapticPatternPreset.SINGLE.createPattern(maximumIntensity, totalDuration)
			: entry.getPattern().createPattern(maximumIntensity, totalDuration);
	}

	public String getDisplayName(CustomPatternLibrary customPatterns)
	{
		if (customPatternId == null)
		{
			return displayName;
		}

		return Objects.requireNonNull(customPatterns, "customPatterns")
			.findById(customPatternId)
			.map(CustomPatternEntry::getName)
			.orElse("Missing custom pattern");
	}

	public boolean isCustom()
	{
		return customPatternId != null;
	}

	public int getCustomPatternId()
	{
		if (customPatternId == null)
		{
			throw new IllegalStateException("A built-in pattern does not have a custom id");
		}
		return customPatternId;
	}

	public String toConfigValue()
	{
		return configValue;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof HapticPatternSelection
			&& configValue.equals(((HapticPatternSelection) other).configValue);
	}

	@Override
	public int hashCode()
	{
		return configValue.hashCode();
	}

	@Override
	public String toString()
	{
		return displayName == null ? configValue : displayName;
	}

	private static HapticPatternSelection builtIn(
		String configValue,
		String displayName,
		HapticPatternPreset preset)
	{
		return new HapticPatternSelection(configValue, displayName, preset, null);
	}

	private static int legacyCustomId(String value)
	{
		switch (value)
		{
			case "CUSTOM_I":
				return 1;
			case "CUSTOM_II":
				return 2;
			case "CUSTOM_III":
				return 3;
			case "CUSTOM_IV":
				return 4;
			default:
				return 0;
		}
	}
}
