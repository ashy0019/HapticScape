package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.ResourceChangedEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;

/** Translates RuneLite HP, prayer and special-energy state into neutral events. */
public final class RuneLiteResourceAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;

	public Optional<ResourceChangedEvent> adapt(StatChanged event)
	{
		Objects.requireNonNull(event, "event");
		return adapt(event.getSkill(), event.getBoostedLevel(), event.getLevel());
	}

	Optional<ResourceChangedEvent> adapt(Skill skill, int currentValue, int maximumValue)
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

	public Optional<ResourceChangedEvent> adapt(VarbitChanged event)
	{
		Objects.requireNonNull(event, "event");
		return adaptVarp(event.getVarpId(), event.getValue());
	}

	Optional<ResourceChangedEvent> adaptVarp(int varpId, int value)
	{
		if (varpId != VarPlayerID.SA_ENERGY)
		{
			return Optional.empty();
		}
		return Optional.of(specialAttackFromVarp(value));
	}

	public ResourceChangedEvent hitpoints(int currentValue, int maximumValue)
	{
		return new ResourceChangedEvent(
			SOURCE,
			ResourceChangedEvent.Kind.HITPOINTS,
			currentValue,
			Math.max(1, maximumValue)
		);
	}

	public ResourceChangedEvent prayer(int currentValue, int maximumValue)
	{
		return new ResourceChangedEvent(
			SOURCE,
			ResourceChangedEvent.Kind.PRAYER,
			currentValue,
			Math.max(1, maximumValue)
		);
	}

	public ResourceChangedEvent specialAttackFromVarp(int rawValue)
	{
		return new ResourceChangedEvent(
			SOURCE,
			ResourceChangedEvent.Kind.SPECIAL_ATTACK,
			clamp(rawValue / 10, 0, 100),
			100
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
