package com.ashy0019.hapticscape.event;

import java.util.Objects;

/**
 * Source-neutral XP event. It intentionally contains no RuneLite types so it can
 * later cross the desktop IPC boundary unchanged.
 */
public final class XpEvent implements HapticScapeEvent
{
	public static final String SOURCE_RUNELITE = "runelite";
	public static final String TYPE = "xp";

	private final String source;
	private final String skillId;
	private final int previousXp;
	private final int currentXp;
	private final int gainedXp;
	private final int previousLevel;
	private final int currentLevel;

	public XpEvent(
		String source,
		String skillId,
		int previousXp,
		int currentXp,
		int gainedXp,
		int previousLevel,
		int currentLevel)
	{
		this.source = requireIdentifier(source, "source");
		this.skillId = requireIdentifier(skillId, "skillId");
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
