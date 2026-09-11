package com.ashy0019.localeventbridge.event;

import com.ashy0019.localeventbridge.EventIdentifiers;
import java.util.Objects;

/**
 * Source-neutral experience event. Source-specific types and identifiers are translated
 * before this event crosses the local event boundary.
 */
public final class ExperienceChangedEvent implements LocalEvent
{
	public static final String TYPE = "experience.changed";

	private final String source;
	private final String skillId;
	private final int previousXp;
	private final int currentXp;
	private final int gainedXp;
	private final int previousLevel;
	private final int currentLevel;

	public ExperienceChangedEvent(
		String source,
		String skillId,
		int previousXp,
		int currentXp,
		int gainedXp,
		int previousLevel,
		int currentLevel)
	{
		this.source = requireIdentifier(source, "source");
		this.skillId = EventIdentifiers.canonical(skillId);
		this.previousXp = previousXp;
		this.currentXp = currentXp;
		this.gainedXp = Math.max(0, gainedXp);
		this.previousLevel = previousLevel;
		this.currentLevel = currentLevel;
	}

	@Override
	public String getSource()
	{
		return source;
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	public String getSkillId()
	{
		return skillId;
	}

	public int getPreviousXp()
	{
		return previousXp;
	}

	public int getCurrentXp()
	{
		return currentXp;
	}

	public int getGainedXp()
	{
		return gainedXp;
	}

	public int getPreviousLevel()
	{
		return previousLevel;
	}

	public int getCurrentLevel()
	{
		return currentLevel;
	}

	public boolean isLevelUp()
	{
		return currentLevel > previousLevel;
	}

	public boolean crossedLevel(int level)
	{
		return previousLevel < level && currentLevel >= level;
	}

	public boolean crossedDecadeMilestone()
	{
		for (int level = 10; level <= 90; level += 10)
		{
			if (crossedLevel(level))
			{
				return true;
			}
		}
		return false;
	}

	private static String requireIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}
}
