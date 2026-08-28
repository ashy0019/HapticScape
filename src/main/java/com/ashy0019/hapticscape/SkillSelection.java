package com.ashy0019.hapticscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Immutable selection of skills which may trigger XP feedback.
 *
 * <p>The configuration stores disabled skills rather than enabled skills. This
 * means a skill added to RuneLite in the future is enabled by default until the
 * user explicitly disables it.</p>
 */
public final class SkillSelection
{
	private static final List<Skill> SELECTABLE_SKILLS = createSelectableSkills();

	private final Set<Skill> disabledSkills;

	private SkillSelection(Set<Skill> disabledSkills)
	{
		EnumSet<Skill> copy = EnumSet.noneOf(Skill.class);
		copy.addAll(disabledSkills);
		this.disabledSkills = Collections.unmodifiableSet(copy);
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

		EnumSet<Skill> disabled = EnumSet.noneOf(Skill.class);
		for (String token : configuredValue.split(","))
		{
			String skillName = token.trim();
			if (skillName.isEmpty())
			{
				continue;
			}

			try
			{
				Skill skill = Skill.valueOf(skillName.toUpperCase(Locale.ROOT));
				disabled.add(skill);
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore values belonging to skills this RuneLite version does not know.
			}
		}
		return new SkillSelection(disabled);
	}

	public boolean isEnabled(Skill skill)
	{
		Objects.requireNonNull(skill, "skill");
		return !disabledSkills.contains(skill);
	}

	public SkillSelection withEnabled(Skill skill, boolean enabled)
	{
		Objects.requireNonNull(skill, "skill");
		EnumSet<Skill> updated = EnumSet.noneOf(Skill.class);
		updated.addAll(disabledSkills);
		if (enabled)
		{
			updated.remove(skill);
		}
		else
		{
			updated.add(skill);
		}
		return updated.equals(disabledSkills) ? this : new SkillSelection(updated);
	}

	public SkillSelection withAllEnabled(boolean enabled)
	{
		if (enabled)
		{
			return allEnabled();
		}

		EnumSet<Skill> disabled = EnumSet.noneOf(Skill.class);
		disabled.addAll(SELECTABLE_SKILLS);
		return new SkillSelection(disabled);
	}

	public int getEnabledCount()
	{
		return SELECTABLE_SKILLS.size() - disabledSkills.size();
	}

	public String toConfigValue()
	{
		StringBuilder result = new StringBuilder();
		for (Skill skill : SELECTABLE_SKILLS)
		{
			if (!disabledSkills.contains(skill))
			{
				continue;
			}

			if (result.length() > 0)
			{
				result.append(',');
			}
			result.append(skill.name());
		}
		return result.toString();
	}

	public static List<Skill> getSelectableSkills()
	{
		return SELECTABLE_SKILLS;
	}

	private static List<Skill> createSelectableSkills()
	{
		List<Skill> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			skills.add(skill);
		}
		return Collections.unmodifiableList(skills);
	}
}
