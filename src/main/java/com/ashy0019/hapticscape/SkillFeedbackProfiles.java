package com.ashy0019.hapticscape;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import net.runelite.api.Skill;

public final class SkillFeedbackProfiles
{
	private static final String VERSION_PREFIX = "v1|";

	private final Map<Skill, XpFeedbackSettings> overrides;

	private SkillFeedbackProfiles(Map<Skill, XpFeedbackSettings> overrides)
	{
		EnumMap<Skill, XpFeedbackSettings> copy = new EnumMap<>(Skill.class);
		copy.putAll(overrides);
		this.overrides = Collections.unmodifiableMap(copy);
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

		EnumMap<Skill, XpFeedbackSettings> parsed = new EnumMap<>(Skill.class);
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
				Skill skill = Skill.valueOf(fields[0].trim().toUpperCase(Locale.ROOT));
				int minimumXpGain = Integer.parseInt(fields[1].trim());
				int intensityPercent = Integer.parseInt(fields[2].trim());
				int durationMillis = Integer.parseInt(fields[3].trim());
				HapticPatternSelection pattern = HapticPatternSelection.valueOf(
					fields[4].trim().toUpperCase(Locale.ROOT));
				parsed.put(
					skill,
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
				// Ignore unknown skills, removed presets, and invalid numeric values.
			}
		}
		return new SkillFeedbackProfiles(parsed);
	}

	public Optional<XpFeedbackSettings> getOverride(Skill skill)
	{
		return Optional.ofNullable(overrides.get(Objects.requireNonNull(skill, "skill")));
	}

	public XpFeedbackSettings resolve(Skill skill, XpFeedbackSettings globalSettings)
	{
		Objects.requireNonNull(globalSettings, "globalSettings");
		return getOverride(skill).orElse(globalSettings);
	}

	public SkillFeedbackProfiles withOverride(Skill skill, XpFeedbackSettings settings)
	{
		EnumMap<Skill, XpFeedbackSettings> updated = new EnumMap<>(Skill.class);
		updated.putAll(overrides);
		updated.put(
			Objects.requireNonNull(skill, "skill"),
			Objects.requireNonNull(settings, "settings")
		);
		return new SkillFeedbackProfiles(updated);
	}

	public SkillFeedbackProfiles withoutOverride(Skill skill)
	{
		Objects.requireNonNull(skill, "skill");
		if (!overrides.containsKey(skill))
		{
			return this;
		}

		EnumMap<Skill, XpFeedbackSettings> updated = new EnumMap<>(Skill.class);
		updated.putAll(overrides);
		updated.remove(skill);
		return new SkillFeedbackProfiles(updated);
	}

	public boolean isEmpty()
	{
		return overrides.isEmpty();
	}

	public String toConfigValue()
	{
		if (overrides.isEmpty())
		{
			return "";
		}

		StringJoiner entries = new StringJoiner(";");
		for (Skill skill : Skill.values())
		{
			XpFeedbackSettings settings = overrides.get(skill);
			if (settings == null)
			{
				continue;
			}

			entries.add(
				skill.name()
					+ "," + settings.getMinimumXpGain()
					+ "," + settings.getIntensityPercent()
					+ "," + settings.getDurationMillis()
					+ "," + settings.getPatternSelection().name()
			);
		}
		return VERSION_PREFIX + entries;
	}
}
