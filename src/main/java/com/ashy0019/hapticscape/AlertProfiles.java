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
	private static final String VERSION_PREFIX = "v2|";

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
			new AlertProfile(AlertBehavior.USE_GENERIC, 35, 350, HapticPatternSelection.DOUBLE)
		);
		defaults.put(
			AlertCategory.TRADE_REQUEST,
			new AlertProfile(AlertBehavior.USE_GENERIC, 45, 600, HapticPatternSelection.TRIPLE)
		);
		defaults.put(
			AlertCategory.LOW_HITPOINTS,
			new AlertProfile(AlertBehavior.OFF, 75, 900, HapticPatternSelection.TRIPLE)
		);
		defaults.put(
			AlertCategory.LOW_PRAYER,
			new AlertProfile(AlertBehavior.OFF, 65, 700, HapticPatternSelection.DOUBLE)
		);
		defaults.put(
			AlertCategory.VALUABLE_DROP,
			new AlertProfile(AlertBehavior.OFF, 70, 1_200, HapticPatternSelection.ASCENDING)
		);
		defaults.put(
			AlertCategory.INVENTORY_FULL,
			new AlertProfile(AlertBehavior.OFF, 45, 500, HapticPatternSelection.DOUBLE)
		);
		defaults.put(
			AlertCategory.POISONED_OR_VENOMED,
			new AlertProfile(AlertBehavior.OFF, 65, 900, HapticPatternSelection.TRIPLE)
		);
		defaults.put(
			AlertCategory.SPECIAL_ATTACK_READY,
			new AlertProfile(AlertBehavior.OFF, 55, 700, HapticPatternSelection.ASCENDING)
		);
		defaults.put(
			AlertCategory.PLAYER_DEATH,
			new AlertProfile(AlertBehavior.OFF, 80, 1_400, HapticPatternSelection.DESCENDING)
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
		boolean legacy = trimmed.startsWith("v1|");
		if (!legacy && !trimmed.startsWith(VERSION_PREFIX))
		{
			return defaults;
		}

		EnumMap<AlertCategory, AlertProfile> parsed = new EnumMap<>(AlertCategory.class);
		parsed.putAll(defaults.profiles);
		for (String entry : trimmed.substring(3).split(";"))
		{
			String[] fields = entry.split(",", -1);
			if (fields.length != (legacy ? 6 : 5))
			{
				continue;
			}

			try
			{
				AlertCategory category = AlertCategory.valueOf(
					fields[0].trim().toUpperCase(Locale.ROOT)
				);
				AlertBehavior behavior = AlertBehavior.valueOf(
					fields[1].trim().toUpperCase(Locale.ROOT)
				);
				int intensity = Integer.parseInt(fields[2].trim());
				int duration = Integer.parseInt(fields[3].trim());
				HapticPatternSelection pattern =
					HapticPatternSelection.fromConfigValue(fields[4]);
				parsed.put(
					category,
					new AlertProfile(behavior, intensity, duration, pattern)
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
		return profiles.get(Objects.requireNonNull(category, "category"));
	}

	public AlertProfiles withProfile(AlertCategory category, AlertProfile profile)
	{
		EnumMap<AlertCategory, AlertProfile> updated = new EnumMap<>(AlertCategory.class);
		updated.putAll(profiles);
		updated.put(
			Objects.requireNonNull(category, "category"),
			Objects.requireNonNull(profile, "profile")
		);
		return new AlertProfiles(updated);
	}

	/**
	 * Resolves a specific semantic alert. Generic notification enablement only
	 * controls catch-all RuneLite notifications; it does not disable profiles
	 * which inherit the generic pattern settings.
	 */
	public Optional<AlertPlayback> resolve(
		AlertCategory category,
		NotificationFeedbackSettings genericSettings)
	{
		Objects.requireNonNull(genericSettings, "genericSettings");
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
			AlertProfile profile = profiles.get(category);
			entries.add(
				category.name()
					+ "," + profile.getBehavior().name()
					+ "," + profile.getIntensityPercent()
					+ "," + profile.getDurationMillis()
					+ "," + profile.getPatternSelection().toConfigValue()
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
