package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.XpEvent;
import com.ashy0019.hapticscape.event.XpEventTracker;
import java.util.Locale;
import java.util.Objects;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Owns translation between RuneLite's XP model and HapticScape's source-neutral
 * XP event. RuneLite types should not escape this integration boundary.
 */
public final class RuneLiteXpEventAdapter
{
	public XpEvent update(XpEventTracker tracker, Skill skill, int currentXp)
	{
		Objects.requireNonNull(tracker, "tracker");
		Objects.requireNonNull(skill, "skill");
		return tracker.update(
			XpEvent.SOURCE_RUNELITE,
			skillId(skill),
			currentXp,
			realLevelForXp(currentXp)
		);
	}

	public void seed(XpEventTracker tracker, Skill skill, int currentXp)
	{
		Objects.requireNonNull(tracker, "tracker");
		Objects.requireNonNull(skill, "skill");
		tracker.seed(
			XpEvent.SOURCE_RUNELITE,
			skillId(skill),
			currentXp,
			realLevelForXp(currentXp)
		);
	}

	private static String skillId(Skill skill)
	{
		return skill.name().toLowerCase(Locale.ROOT);
	}

	private static int realLevelForXp(int xp)
	{
		return Math.min(99, Experience.getLevelForXp(Math.max(0, xp)));
	}

	/**
	 * Temporary compatibility seam for code that still requires RuneLite Skill.
	 * Delete this direction once the remaining RuneLite-owned consumers move out.
	 */
	public Skill toSkill(XpEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (!XpEvent.SOURCE_RUNELITE.equals(event.getSource()))
		{
			throw new IllegalArgumentException(
				"Cannot convert non-RuneLite XP event to RuneLite Skill"
			);
		}
		return Skill.valueOf(event.getSkillId().toUpperCase(Locale.ROOT));
	}
}
