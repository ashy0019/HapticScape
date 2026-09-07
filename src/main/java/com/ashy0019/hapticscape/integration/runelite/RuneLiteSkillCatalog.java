package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.SkillDescriptor;
import com.ashy0019.hapticscape.SkillIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/** RuneLite edge helper which translates RuneLite Skill values into neutral skill IDs. */
public final class RuneLiteSkillCatalog
{
	private static final List<Skill> SELECTABLE_SKILLS = createSelectableSkills();
	private static final List<String> SKILL_IDS = createSkillIds();
	private static final SkillCatalog NEUTRAL_CATALOG = createNeutralCatalog();

	private RuneLiteSkillCatalog()
	{
	}

	public static List<Skill> getSelectableSkills()
	{
		return SELECTABLE_SKILLS;
	}

	public static List<String> getSkillIds()
	{
		return SKILL_IDS;
	}

	public static SkillCatalog getNeutralCatalog()
	{
		return NEUTRAL_CATALOG;
	}

	public static String skillId(Skill skill)
	{
		return SkillIds.canonical(skill.name());
	}

	private static List<Skill> createSelectableSkills()
	{
		List<Skill> skills = new ArrayList<>();
		Collections.addAll(skills, Skill.values());
		return Collections.unmodifiableList(skills);
	}

	private static List<String> createSkillIds()
	{
		List<String> skillIds = new ArrayList<>();
		for (Skill skill : SELECTABLE_SKILLS)
		{
			skillIds.add(skillId(skill));
		}
		return Collections.unmodifiableList(skillIds);
	}

	private static SkillCatalog createNeutralCatalog()
	{
		List<SkillDescriptor> skills = new ArrayList<>();
		for (Skill skill : SELECTABLE_SKILLS)
		{
			skills.add(new SkillDescriptor(skillId(skill), skill.getName()));
		}
		return new SkillCatalog(skills);
	}
}
