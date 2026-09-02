package com.ashy0019.hapticscape;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

public final class AlertProfiles
{
	private static final String VERSION_PREFIX = "v1|";

	private final Map<AlertCategory, AlertProfile> profiles;

	private AlertProfiles(Map<AlertCategory, AlertProfile> profiles)
	{
		EnumMap<AlertCategory, AlertProfile> copy = new EnumMap<>(AlertCategory.class);
		copy.putAll(profiles);
		this.profiles = Collections.unmodifiableMap(copy);
	}

	public static AlertProfiles defaults()
	{
		EnumMap<AlertCategory, AlertProfile> defaults = new EnumMap<>(AlertCategory.class);
		defaults.put(
			AlertCategory.DIRECT_MESSAGE,
			new AlertProfile(AlertBehavior.USE_GENERIC, 35, 350, HapticPatternSelection.DOUBLE, 1)
		);
		defaults.put(
			AlertCategory.TRADE_REQUEST,
			new AlertProfile(AlertBehavior.USE_GENERIC, 45, 600, HapticPatternSelection.TRIPLE, 1)
		);
		defaults.put(
			AlertCategory.LOW_HITPOINTS,
			new AlertProfile(AlertBehavior.OFF, 75, 900, HapticPatternSelection.TRIPLE, 20)
		);
		defaults.put(
			AlertCategory.LOW_PRAYER,
			new AlertProfile(AlertBehavior.OFF, 65, 700, HapticPatternSelection.DOUBLE, 10)
		);
		return new AlertProfiles(defaults);
	}

	public static AlertProfiles fromConfigValue(String configuredValue)
	{
		AlertProfiles defaults = defaults();
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return defaults;
		}

		String trimmed = configuredValue.trim();
		if (!trimmed.startsWith(VERSION_PREFIX))
		{
			return defaults;
		}

		EnumMap<AlertCategory, AlertProfile> parsed = new EnumMap<>(AlertCategory.class);
		parsed.putAll(defaults.profiles);
		for (String entry : trimmed.substring(VERSION_PREFIX.length()).split(";"))
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
				if (category == AlertCategory.GENERIC_NOTIFICATION)
				{
					continue;
				}
				AlertBehavior behavior = AlertBehavior.valueOf(
					fields[1].trim().toUpperCase(Locale.ROOT)
				);
				int intensity = Integer.parseInt(fields[2].trim());
				int duration = Integer.parseInt(fields[3].trim());
				HapticPatternSelection pattern =
					HapticPatternSelection.fromConfigValue(fields[4]);
				int threshold = Integer.parseInt(fields[5].trim());
				parsed.put(
					category,
					new AlertProfile(behavior, intensity, duration, pattern, threshold)
				);
			}
			catch (IllegalArgumentException ignored)
			{
				// Keep the safe default for malformed or unknown entries.
			}
		}
		return new AlertProfiles(parsed);
	}

	public AlertProfile get(AlertCategory category)
	{
		AlertCategory required = Objects.requireNonNull(category, "category");
		if (required == AlertCategory.GENERIC_NOTIFICATION)
		{
			throw new IllegalArgumentException("The generic profile uses the legacy notification settings");
		}
		return profiles.get(required);
	}

	public AlertProfiles withProfile(AlertCategory category, AlertProfile profile)
	{
		if (category == AlertCategory.GENERIC_NOTIFICATION)
		{
			throw new IllegalArgumentException("The generic profile is stored separately");
		}
		EnumMap<AlertCategory, AlertProfile> updated = new EnumMap<>(AlertCategory.class);
		updated.putAll(profiles);
		updated.put(category, Objects.requireNonNull(profile, "profile"));
		return new AlertProfiles(updated);
	}

	public Optional<AlertPlayback> resolve(
		AlertCategory category,
		NotificationFeedbackSettings genericSettings)
	{
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(genericSettings, "genericSettings");
		if (!genericSettings.isEnabled())
		{
			return Optional.empty();
		}

		if (category == AlertCategory.GENERIC_NOTIFICATION)
		{
			return Optional.of(genericPlayback(genericSettings));
		}

		AlertProfile profile = get(category);
		switch (profile.getBehavior())
		{
			case USE_GENERIC:
				return Optional.of(genericPlayback(genericSettings));
			case CUSTOM:
				return Optional.of(new AlertPlayback(
					profile.getPatternSelection(),
					profile.getIntensityPercent(),
					profile.getDurationMillis()
				));
			case OFF:
			default:
				return Optional.empty();
		}
	}

	public AlertProfiles replaceMissingCustomPatterns(CustomPatternLibrary customPatterns)
	{
		EnumMap<AlertCategory, AlertProfile> updated = new EnumMap<>(AlertCategory.class);
		boolean changed = false;
		for (Map.Entry<AlertCategory, AlertProfile> entry : profiles.entrySet())
		{
			AlertProfile profile = entry.getValue();
			HapticPatternSelection resolved = profile.getPatternSelection()
				.resolveAgainst(customPatterns);
			if (!resolved.equals(profile.getPatternSelection()))
			{
				profile = profile.withPattern(resolved);
				changed = true;
			}
			updated.put(entry.getKey(), profile);
		}
		return changed ? new AlertProfiles(updated) : this;
	}

	public String toConfigValue()
	{
		StringJoiner entries = new StringJoiner(";");
		for (AlertCategory category : AlertCategory.values())
		{
			if (category == AlertCategory.GENERIC_NOTIFICATION)
			{
				continue;
			}
			AlertProfile profile = profiles.get(category);
			entries.add(
				category.name()
					+ "," + profile.getBehavior().name()
					+ "," + profile.getIntensityPercent()
					+ "," + profile.getDurationMillis()
					+ "," + profile.getPatternSelection().toConfigValue()
					+ "," + profile.getThreshold()
			);
		}
		return VERSION_PREFIX + entries;
	}

	private static AlertPlayback genericPlayback(NotificationFeedbackSettings settings)
	{
		return new AlertPlayback(
			settings.getPatternSelection(),
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}
}
