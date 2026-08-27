package com.ashy0019.hapticscape;

import java.util.Arrays;
import net.runelite.api.Skill;

public final class XpTracker
{
	private final int[] previousXp = new int[Skill.values().length];
	private final boolean[] initialized = new boolean[Skill.values().length];

	public int update(Skill skill, int currentXp)
	{
		int skillIndex = skill.ordinal();

		if (!initialized[skillIndex])
		{
			seed(skill, currentXp);
			return 0;
		}

		int gainedXp = currentXp - previousXp[skillIndex];
		previousXp[skillIndex] = currentXp;

		return Math.max(gainedXp, 0);
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
}
