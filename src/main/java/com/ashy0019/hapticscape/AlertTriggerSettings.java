package com.ashy0019.hapticscape;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class AlertTriggerSettings
{
	private static final String VERSION_PREFIX = "v1|";

	private final Map<AlertCategory, Integer> values;

	private AlertTriggerSettings(Map<AlertCategory, Integer> values)
	{
		EnumMap<AlertCategory, Integer> copy = new EnumMap<>(AlertCategory.class);
		copy.putAll(values);
		this.values = Collections.unmodifiableMap(copy);
	}

	public static AlertTriggerSettings defaults()
	{
		EnumMap<AlertCategory, Integer> defaults = new EnumMap<>(AlertCategory.class);
		for (AlertCategory category : AlertCategory.values())
		{
			if (category.hasTriggerParameter())
			{
				defaults.put(category, category.getTriggerParameter().getDefaultValue());
			}
		}
		return new AlertTriggerSettings(defaults);
	}

	public static AlertTriggerSettings fromConfigValues(
		String configuredValue,
		String legacyProfilesValue)
	{
		AlertTriggerSettings defaults = defaults();
		if (configuredValue != null && configuredValue.trim().startsWith(VERSION_PREFIX))
		{
			EnumMap<AlertCategory, Integer> parsed = new EnumMap<>(AlertCategory.class);
			parsed.putAll(defaults.values);
			for (String entry : configuredValue.trim()
				.substring(VERSION_PREFIX.length()).split(";"))
			{
				String[] fields = entry.split("=", -1);
				if (fields.length != 2)
				{
					continue;
				}
				try
				{
					AlertCategory category = AlertCategory.valueOf(
						fields[0].trim().toUpperCase(Locale.ROOT)
					);
					if (category.hasTriggerParameter())
					{
						int value = Integer.parseInt(fields[1].trim());
						AlertTriggerParameter parameter = category.getTriggerParameter();
						if (value >= parameter.getMinimum() && value <= parameter.getMaximum())
						{
							parsed.put(category, value);
						}
					}
				}
				catch (IllegalArgumentException ignored)
				{
					// Keep the safe default for malformed or unknown entries.
				}
			}
			return new AlertTriggerSettings(parsed);
		}

		return migrateLegacyProfiles(legacyProfilesValue, defaults);
	}

	public int get(AlertCategory category)
	{
		AlertCategory required = Objects.requireNonNull(category, "category");
		if (!required.hasTriggerParameter())
		{
			throw new IllegalArgumentException(required + " does not have a trigger value");
		}
		return values.get(required);
	}

	public AlertTriggerSettings withValue(AlertCategory category, int value)
	{
		AlertTriggerParameter parameter = Objects.requireNonNull(category, "category")
			.getTriggerParameter();
		if (value < parameter.getMinimum() || value > parameter.getMaximum())
		{
			throw new IllegalArgumentException(
				"Trigger value must be between " + parameter.getMinimum()
					+ " and " + parameter.getMaximum()
			);
		}

		EnumMap<AlertCategory, Integer> updated = new EnumMap<>(AlertCategory.class);
		updated.putAll(values);
		updated.put(category, value);
		return new AlertTriggerSettings(updated);
	}

	public String toConfigValue()
	{
		StringJoiner entries = new StringJoiner(";");
		for (AlertCategory category : AlertCategory.values())
		{
			if (category.hasTriggerParameter())
			{
				entries.add(category.name() + "=" + values.get(category));
			}
		}
		return VERSION_PREFIX + entries;
	}

	private static AlertTriggerSettings migrateLegacyProfiles(
		String legacyProfilesValue,
		AlertTriggerSettings defaults)
	{
		if (legacyProfilesValue == null || !legacyProfilesValue.trim().startsWith("v1|"))
		{
			return defaults;
		}

		EnumMap<AlertCategory, Integer> migrated = new EnumMap<>(AlertCategory.class);
		migrated.putAll(defaults.values);
		for (String entry : legacyProfilesValue.trim().substring(3).split(";"))
		{
			String[] fields = entry.split(",", -1);
			if (fields.length != 6)
			{
				continue;
			}
			try
			{
				AlertCategory category = AlertCategory.valueOf(
					fields[0].trim().toUpperCase(Locale.ROOT)
				);
				if (category.hasTriggerParameter())
				{
					int value = Integer.parseInt(fields[5].trim());
					AlertTriggerParameter parameter = category.getTriggerParameter();
					if (value >= parameter.getMinimum() && value <= parameter.getMaximum())
					{
						migrated.put(category, value);
					}
				}
			}
			catch (IllegalArgumentException ignored)
			{
				// Keep the safe default for malformed or unknown entries.
			}
		}
		return new AlertTriggerSettings(migrated);
	}
}
