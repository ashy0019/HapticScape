package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/** Source-neutral per-skill XP click overrides keyed by canonical skill ID. */
public final class SkillClickProfiles
{
	private static final String VERSION_PREFIX = "v1|";

	private final Map<String, ClickerXpSettings> overrides;

	private SkillClickProfiles(Map<String, ClickerXpSettings> overrides)
	{
		this.overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
	}

	public static SkillClickProfiles empty()
	{
		return new SkillClickProfiles(Collections.emptyMap());
	}

	public static SkillClickProfiles fromConfigValue(String configuredValue)
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

		LinkedHashMap<String, ClickerXpSettings> parsed = new LinkedHashMap<>();
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
				ClickSequence xpGain = requiredSequence(fields[2]);
				ClickSequence levelUp = requiredSequence(fields[3]);
				ClickSequence milestone = requiredSequence(fields[4]);
				parsed.put(
					skillId,
					new ClickerXpSettings(minimumXpGain, xpGain, levelUp, milestone)
				);
			}
			catch (IllegalArgumentException | NullPointerException ignored)
			{
				// Ignore malformed identifiers, sequences, and numeric values independently.
			}
		}
		return new SkillClickProfiles(parsed);
	}

	public Optional<ClickerXpSettings> getOverride(String skillId)
	{
		return Optional.ofNullable(overrides.get(SkillIds.canonical(skillId)));
	}

	public ClickerXpSettings resolve(String skillId, ClickerXpSettings globalSettings)
	{
		Objects.requireNonNull(globalSettings, "globalSettings");
		return getOverride(skillId).orElse(globalSettings);
	}

	public SkillClickProfiles withOverride(String skillId, ClickerXpSettings settings)
	{
		LinkedHashMap<String, ClickerXpSettings> updated = new LinkedHashMap<>(overrides);
		updated.put(
			SkillIds.canonical(skillId),
			Objects.requireNonNull(settings, "settings")
		);
		return new SkillClickProfiles(updated);
	}

	public SkillClickProfiles withoutOverride(String skillId)
	{
		String canonical = SkillIds.canonical(skillId);
		if (!overrides.containsKey(canonical))
		{
			return this;
		}

		LinkedHashMap<String, ClickerXpSettings> updated = new LinkedHashMap<>(overrides);
		updated.remove(canonical);
		return new SkillClickProfiles(updated);
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
		for (Map.Entry<String, ClickerXpSettings> entry : overrides.entrySet())
		{
			ClickerXpSettings settings = entry.getValue();
			entries.add(
				SkillIds.toConfigToken(entry.getKey())
					+ "," + settings.getMinimumXpGain()
					+ "," + settings.getXpGainSequence().toConfigValue()
					+ "," + settings.getLevelUpOverride().toConfigValue()
					+ "," + settings.getMilestoneOverride().toConfigValue()
			);
		}
		return VERSION_PREFIX + entries;
	}

	private static ClickSequence requiredSequence(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			throw new IllegalArgumentException("Missing click sequence");
		}
		return ClickSequence.valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
