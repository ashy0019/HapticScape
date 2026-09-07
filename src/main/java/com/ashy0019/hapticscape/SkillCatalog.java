package com.ashy0019.hapticscape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ordered, source-neutral skill metadata supplied by the active game integration. */
public final class SkillCatalog
{
	private final List<SkillDescriptor> skills;
	private final List<String> skillIds;
	private final Map<String, SkillDescriptor> byId;

	public SkillCatalog(Collection<SkillDescriptor> skills)
	{
		Objects.requireNonNull(skills, "skills");
		List<SkillDescriptor> ordered = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		Map<String, SkillDescriptor> indexed = new LinkedHashMap<>();
		for (SkillDescriptor skill : skills)
		{
			SkillDescriptor value = Objects.requireNonNull(skill, "skill");
			if (indexed.put(value.getId(), value) != null)
			{
				throw new IllegalArgumentException("Duplicate skill ID: " + value.getId());
			}
			ordered.add(value);
			ids.add(value.getId());
		}
		if (ordered.isEmpty())
		{
			throw new IllegalArgumentException("skills must not be empty");
		}
		this.skills = Collections.unmodifiableList(ordered);
		this.skillIds = Collections.unmodifiableList(ids);
		this.byId = Collections.unmodifiableMap(indexed);
	}

	public List<SkillDescriptor> getSkills()
	{
		return skills;
	}

	public List<String> getSkillIds()
	{
		return skillIds;
	}

	public SkillDescriptor require(String skillId)
	{
		String canonical = SkillIds.canonical(skillId);
		SkillDescriptor skill = byId.get(canonical);
		if (skill == null)
		{
			throw new IllegalArgumentException("Unknown skill ID: " + skillId);
		}
		return skill;
	}
}
