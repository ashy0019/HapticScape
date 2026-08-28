package com.ashy0019.hapticscape;

import java.util.Objects;
import net.runelite.api.Skill;

public final class XpChange
{
	private final Skill skill;
	private final int previousXp;
	private final int currentXp;
	private final int gainedXp;
	private final int previousLevel;
	private final int currentLevel;

	XpChange(
		Skill skill,
		int previousXp,
		int currentXp,
		int gainedXp,
		int previousLevel,
		int currentLevel)
	{
		this.skill = Objects.requireNonNull(skill, "skill");
		this.previousXp = previousXp;
		this.currentXp = currentXp;
		this.gainedXp = gainedXp;
		this.previousLevel = previousLevel;
		this.currentLevel = currentLevel;
	}

	public Skill getSkill()
	{
		return skill;
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
}
