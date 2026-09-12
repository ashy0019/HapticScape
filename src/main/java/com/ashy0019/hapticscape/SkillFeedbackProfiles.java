package com.ashy0019.hapticscape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/** Source-neutral per-skill XP feedback overrides keyed by canonical skill ID. */
public final class SkillFeedbackProfiles
{
	private static final String VERSION_PREFIX = "v1|";

	private final Map<String, XpFeedbackSettings> overrides;

	private SkillFeedbackProfiles(Map<String, XpFeedbackSettings> overrides)
	{
		this.overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
	}

	public static SkillFeedbackProfiles empty()
	{
		return new SkillFeedbackProfiles(Collections.emptyMap());
	}

	public static SkillFeedbackProfiles fromConfigValue(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return empty();
		}

		String trimmed = configuredValue.trim();
		if (!trimmed.startsWith(VERSION_PREFIX))
		{
			return empty();
		}

		LinkedHashMap<String, XpFeedbackSettings> parsed = new LinkedHashMap<>();
		String entries = trimmed.substring(VERSION_PREFIX.length());
		for (String entry : entries.split(";"))
		{
			String[] fields = entry.split(",", -1);
			if (fields.length != 5)
			{
				continue;
			}

			try
			{
				String skillId = SkillIds.canonical(fields[0]);
				int minimumXpGain = Integer.parseInt(fields[1].trim());
				int intensityPercent = Integer.parseInt(fields[2].trim());
				int durationMillis = Integer.parseInt(fields[3].trim());
				HapticPatternSelection pattern =
					HapticPatternSelection.fromConfigValue(fields[4]);
				parsed.put(
					skillId,
					new XpFeedbackSettings(
						minimumXpGain,
						intensityPercent,
						durationMillis,
						pattern
					)
				);
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore malformed identifiers, removed presets, and invalid numeric values.
			}
		}
		return new SkillFeedbackProfiles(parsed);
	}

	public Optional<XpFeedbackSettings> getOverride(String skillId)
	{
		return Optional.ofNullable(overrides.get(SkillIds.canonical(skillId)));
	}

	public XpFeedbackSettings resolve(String skillId, XpFeedbackSettings globalSettings)
	{
		Objects.requireNonNull(globalSettings, "globalSettings");
		return getOverride(skillId).orElse(globalSettings);
	}

	public SkillFeedbackProfiles withOverride(String skillId, XpFeedbackSettings settings)
	{
		LinkedHashMap<String, XpFeedbackSettings> updated = new LinkedHashMap<>(overrides);
		updated.put(
			SkillIds.canonical(skillId),
			Objects.requireNonNull(settings, "settings")
		);
		return new SkillFeedbackProfiles(updated);
	}

	public SkillFeedbackProfiles withoutOverride(String skillId)
	{
		String canonical = SkillIds.canonical(skillId);
		if (!overrides.containsKey(canonical))
		{
			return this;
		}

		LinkedHashMap<String, XpFeedbackSettings> updated = new LinkedHashMap<>(overrides);
		updated.remove(canonical);
		return new SkillFeedbackProfiles(updated);
	}

	public boolean isEmpty()
	{
		return overrides.isEmpty();
	}

	public SkillFeedbackProfiles replaceMissingCustomPatterns(
		CustomPatternLibrary customPatterns)
	{
		LinkedHashMap<String, XpFeedbackSettings> updated = new LinkedHashMap<>();
		boolean changed = false;
		for (Map.Entry<String, XpFeedbackSettings> entry : overrides.entrySet())
		{
			XpFeedbackSettings settings = entry.getValue();
			HapticPatternSelection resolved = settings.getPatternSelection()
				.resolveAgainst(customPatterns);
			if (!resolved.equals(settings.getPatternSelection()))
			{
				settings = new XpFeedbackSettings(
					settings.getMinimumXpGain(),
					settings.getIntensityPercent(),
					settings.getDurationMillis(),
					resolved
				);
				changed = true;
			}
			updated.put(entry.getKey(), settings);
		}
		return changed ? new SkillFeedbackProfiles(updated) : this;
	}

	public String toConfigValue()
	{
		if (overrides.isEmpty())
		{
			return "";
		}

		StringJoiner entries = new StringJoiner(";");
		for (Map.Entry<String, XpFeedbackSettings> entry : overrides.entrySet())
		{
			XpFeedbackSettings settings = entry.getValue();
			entries.add(
				SkillIds.toConfigToken(entry.getKey())
					+ "," + settings.getMinimumXpGain()
					+ "," + settings.getIntensityPercent()
					+ "," + settings.getDurationMillis()
					+ "," + settings.getPatternSelection().toConfigValue()
			);
		}
		return VERSION_PREFIX + entries;
	}
}
