package com.ashy0019.hapticscape;

import java.util.Locale;
import java.util.Objects;

/** Canonical source-neutral identifiers used by skill settings and XP events. */
public final class SkillIds
{
	private SkillIds()
	{
	}

	public static String canonical(String skillId)
	{
		String normalized = Objects.requireNonNull(skillId, "skillId")
			.trim()
			.toLowerCase(Locale.ROOT);
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException("skillId must not be empty");
		}
		if (normalized.indexOf(',') >= 0 || normalized.indexOf(';') >= 0)
		{
			throw new IllegalArgumentException("skillId contains a reserved delimiter");
		}
		return normalized;
	}

	public static String toConfigToken(String skillId)
	{
		return canonical(skillId).toUpperCase(Locale.ROOT);
	}
}
