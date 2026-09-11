package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.AlertCategory;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Immutable click sequence selection for semantic alerts.
 *
 * <p>The legacy comma-separated format represented enabled/disabled only;
 * every enabled legacy alert migrates to {@link ClickSequence#ONE}.</p>
 */
public final class ClickerAlertSettings
{
	private static final String FORMAT_VERSION = "v2";
	private final Map<AlertCategory, ClickSequence> sequences;

	private ClickerAlertSettings(Map<AlertCategory, ClickSequence> sequences)
	{
		EnumMap<AlertCategory, ClickSequence> copy = new EnumMap<>(AlertCategory.class);
		for (AlertCategory category : AlertCategory.values())
		{
			copy.put(
				category,
				Objects.requireNonNull(
					sequences.getOrDefault(category, ClickSequence.NONE),
					"sequence"
				)
			);
		}
		this.sequences = copy;
	}

	public static ClickerAlertSettings noneEnabled()
	{
		return new ClickerAlertSettings(new EnumMap<>(AlertCategory.class));
	}

	public static ClickerAlertSettings fromConfigValue(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return noneEnabled();
		}

		String trimmed = configuredValue.trim();
		if (!trimmed.equals(FORMAT_VERSION) && !trimmed.startsWith(FORMAT_VERSION + ";"))
		{
			return fromLegacyConfigValue(trimmed);
		}

		EnumMap<AlertCategory, ClickSequence> restored = new EnumMap<>(AlertCategory.class);
		String[] entries = trimmed.split(";", -1);
		for (int index = 1; index < entries.length; index++)
		{
			String entry = entries[index].trim();
			if (entry.isEmpty())
			{
				continue;
			}
			String[] fields = entry.split("=", 2);
			if (fields.length != 2)
			{
				continue;
			}
			try
			{
				AlertCategory category = AlertCategory.valueOf(
					fields[0].trim().toUpperCase(Locale.ROOT)
				);
				ClickSequence sequence = ClickSequence.fromConfigValue(
					fields[1],
					ClickSequence.NONE
				);
				if (sequence.isEnabled())
				{
					restored.put(category, sequence);
				}
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore unknown categories so future or malformed values are harmless.
			}
		}
		return new ClickerAlertSettings(restored);
	}

	public static boolean requiresMigration(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return false;
		}
		String trimmed = configuredValue.trim();
		return !trimmed.equals(FORMAT_VERSION)
			&& !trimmed.startsWith(FORMAT_VERSION + ";");
	}

	private static ClickerAlertSettings fromLegacyConfigValue(String configuredValue)
	{
		EnumMap<AlertCategory, ClickSequence> restored = new EnumMap<>(AlertCategory.class);
		for (String token : configuredValue.split(","))
		{
			try
			{
				AlertCategory category = AlertCategory.valueOf(
					token.trim().toUpperCase(Locale.ROOT)
				);
				restored.put(category, ClickSequence.ONE);
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore unknown categories so future or malformed values are harmless.
			}
		}
		return new ClickerAlertSettings(restored);
	}

	public ClickSequence getSequence(AlertCategory category)
	{
		return sequences.get(Objects.requireNonNull(category, "category"));
	}

	public boolean isEnabled(AlertCategory category)
	{
		return getSequence(category).isEnabled();
	}

	public ClickerAlertSettings withSequence(AlertCategory category, ClickSequence sequence)
	{
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(sequence, "sequence");
		if (getSequence(category) == sequence)
		{
			return this;
		}
		EnumMap<AlertCategory, ClickSequence> updated = new EnumMap<>(sequences);
		updated.put(category, sequence);
		return new ClickerAlertSettings(updated);
	}

	/** Compatibility helper for the current checkbox UI. */
	public ClickerAlertSettings withEnabled(AlertCategory category, boolean enabled)
	{
		return withSequence(
			category,
			enabled ? ClickSequence.ONE : ClickSequence.NONE
		);
	}

	public String toConfigValue()
	{
		StringJoiner result = new StringJoiner(";", FORMAT_VERSION + ";", "");
		boolean any = false;
		for (AlertCategory category : AlertCategory.values())
		{
			ClickSequence sequence = getSequence(category);
			if (sequence.isEnabled())
			{
				result.add(category.name() + "=" + sequence.toConfigValue());
				any = true;
			}
		}
		return any ? result.toString() : "";
	}
}
