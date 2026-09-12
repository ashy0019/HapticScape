package com.ashy0019.hapticscape;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable source-neutral selection of skill identifiers which may trigger feedback. */
public final class SkillSelection
{
	private final Set<String> disabledSkillIds;

	private SkillSelection(Set<String> disabledSkillIds)
	{
		this.disabledSkillIds = Collections.unmodifiableSet(
			new LinkedHashSet<>(disabledSkillIds)
		);
	}

	public static SkillSelection allEnabled()
	{
		return new SkillSelection(Collections.emptySet());
	}

	public static SkillSelection fromConfigValue(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return allEnabled();
		}

		LinkedHashSet<String> disabled = new LinkedHashSet<>();
		for (String token : configuredValue.split(","))
		{
			String trimmed = token.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}
			try
			{
				disabled.add(SkillIds.canonical(trimmed));
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore malformed identifiers while preserving unknown future identifiers.
			}
		}
		return new SkillSelection(disabled);
	}

	public boolean isEnabled(String skillId)
	{
		return !disabledSkillIds.contains(SkillIds.canonical(skillId));
	}

	public SkillSelection withEnabled(String skillId, boolean enabled)
	{
		String canonical = SkillIds.canonical(skillId);
		LinkedHashSet<String> updated = new LinkedHashSet<>(disabledSkillIds);
		if (enabled)
		{
			updated.remove(canonical);
		}
		else
		{
			updated.add(canonical);
		}
		return updated.equals(disabledSkillIds) ? this : new SkillSelection(updated);
	}

	public SkillSelection withAllEnabled(Collection<String> skillIds, boolean enabled)
	{
		Objects.requireNonNull(skillIds, "skillIds");
		SkillSelection updated = this;
		for (String skillId : skillIds)
		{
			updated = updated.withEnabled(skillId, enabled);
		}
		return updated;
	}

	public int getEnabledCount(Collection<String> selectableSkillIds)
	{
		Objects.requireNonNull(selectableSkillIds, "selectableSkillIds");
		int enabled = 0;
		for (String skillId : selectableSkillIds)
		{
			if (isEnabled(skillId))
			{
				enabled++;
			}
		}
		return enabled;
	}

	public String toConfigValue()
	{
		StringBuilder result = new StringBuilder();
		for (String skillId : disabledSkillIds)
		{
			if (result.length() > 0)
			{
				result.append(',');
			}
			result.append(SkillIds.toConfigToken(skillId));
		}
		return result.toString();
	}
}
