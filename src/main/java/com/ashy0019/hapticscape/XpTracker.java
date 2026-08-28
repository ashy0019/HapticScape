package com.ashy0019.hapticscape;

import java.util.Arrays;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

public final class XpTracker
{
	private final int[] previousXp = new int[Skill.values().length];
	private final boolean[] initialized = new boolean[Skill.values().length];

	public XpChange update(Skill skill, int currentXp)
	{
		int skillIndex = skill.ordinal();

		if (!initialized[skillIndex])
		{
			seed(skill, currentXp);
			int currentLevel = realLevelForXp(currentXp);
			return new XpChange(
				skill,
				currentXp,
				currentXp,
				0,
				currentLevel,
				currentLevel
			);
		}

		int previousSkillXp = previousXp[skillIndex];
		int gainedXp = currentXp - previousSkillXp;
		previousXp[skillIndex] = currentXp;

		return new XpChange(
			skill,
			previousSkillXp,
			currentXp,
			Math.max(gainedXp, 0),
			realLevelForXp(previousSkillXp),
			realLevelForXp(currentXp)
		);
	}

	public void seed(Skill skill, int currentXp)
	{
		int skillIndex = skill.ordinal();
		previousXp[skillIndex] = currentXp;
		initialized[skillIndex] = true;
	}

	public void reset()
	{
		Arrays.fill(previousXp, 0);
		Arrays.fill(initialized, false);
	}

	private static int realLevelForXp(int xp)
	{
		return Math.min(99, Experience.getLevelForXp(Math.max(0, xp)));
	}
}
