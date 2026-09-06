package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.XpChange;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Locale;
import java.util.Objects;
import net.runelite.api.Skill;

/**
 * Owns translation between RuneLite's XP model and HapticScape's source-neutral
 * XP event. RuneLite types should not escape this integration boundary.
 */
public final class RuneLiteXpEventAdapter
{
	public XpEvent from(XpChange change)
	{
		Objects.requireNonNull(change, "change");
		return new XpEvent(
			XpEvent.SOURCE_RUNELITE,
			change.getSkill().name().toLowerCase(Locale.ROOT),
			change.getPreviousXp(),
			change.getCurrentXp(),
			change.getGainedXp(),
			change.getPreviousLevel(),
			change.getCurrentLevel()
		);
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
