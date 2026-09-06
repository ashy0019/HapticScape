package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;

/** Translates RuneLite HP, prayer and special-energy state into neutral events. */
public final class RuneLiteVitalsEventAdapter
{
	private static final String SOURCE = "runelite";

	public Optional<VitalsChangedEvent> adapt(StatChanged event)
	{
		Objects.requireNonNull(event, "event");
		return adapt(event.getSkill(), event.getBoostedLevel(), event.getLevel());
	}

	Optional<VitalsChangedEvent> adapt(Skill skill, int currentValue, int maximumValue)
	{
		Objects.requireNonNull(skill, "skill");
		if (skill == Skill.HITPOINTS)
		{
			return Optional.of(hitpoints(currentValue, maximumValue));
		}
		if (skill == Skill.PRAYER)
		{
			return Optional.of(prayer(currentValue, maximumValue));
		}
		return Optional.empty();
	}

	public Optional<VitalsChangedEvent> adapt(VarbitChanged event)
	{
		Objects.requireNonNull(event, "event");
		return adaptVarp(event.getVarpId(), event.getValue());
	}

	Optional<VitalsChangedEvent> adaptVarp(int varpId, int value)
	{
		if (varpId != VarPlayerID.SA_ENERGY)
		{
			return Optional.empty();
		}
		return Optional.of(specialAttackFromVarp(value));
	}

	public VitalsChangedEvent hitpoints(int currentValue, int maximumValue)
	{
		return new VitalsChangedEvent(
			SOURCE,
			VitalsChangedEvent.Kind.HITPOINTS,
			currentValue,
			Math.max(1, maximumValue)
		);
	}

	public VitalsChangedEvent prayer(int currentValue, int maximumValue)
	{
		return new VitalsChangedEvent(
			SOURCE,
			VitalsChangedEvent.Kind.PRAYER,
			currentValue,
			Math.max(1, maximumValue)
		);
	}

	public VitalsChangedEvent specialAttackFromVarp(int rawValue)
	{
		return new VitalsChangedEvent(
			SOURCE,
			VitalsChangedEvent.Kind.SPECIAL_ATTACK,
			clamp(rawValue / 10, 0, 100),
			100
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
