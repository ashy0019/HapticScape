package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.EventIdentifiers;
import com.ashy0019.localeventbridge.event.ExperienceChangedEvent;
import com.ashy0019.localeventbridge.event.ExperienceTracker;
import java.util.Objects;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Owns translation between RuneLite's XP model and the source-neutral
 * experience event. RuneLite types should not escape this integration boundary.
 */
public final class RuneLiteExperienceAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;

	public ExperienceChangedEvent update(ExperienceTracker tracker, Skill skill, int currentXp)
	{
		Objects.requireNonNull(tracker, "tracker");
		Objects.requireNonNull(skill, "skill");
		return tracker.update(
			SOURCE,
			skillId(skill),
			currentXp,
			realLevelForXp(currentXp)
		);
	}

	public void seed(ExperienceTracker tracker, Skill skill, int currentXp)
	{
		Objects.requireNonNull(tracker, "tracker");
		Objects.requireNonNull(skill, "skill");
		tracker.seed(
			SOURCE,
			skillId(skill),
			currentXp,
			realLevelForXp(currentXp)
		);
	}

	private static String skillId(Skill skill)
	{
		return EventIdentifiers.canonical(skill.name());
	}

	private static int realLevelForXp(int xp)
	{
		return Math.min(99, Experience.getLevelForXp(Math.max(0, xp)));
	}

}
