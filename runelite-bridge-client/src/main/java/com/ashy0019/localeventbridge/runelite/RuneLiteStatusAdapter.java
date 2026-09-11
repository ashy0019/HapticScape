package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.StatusChangedEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;

/** Translates RuneLite's poison varp encoding into a neutral toxic status. */
public final class RuneLiteStatusAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;
	private static final int VENOM_THRESHOLD = 1_000_000;

	public Optional<StatusChangedEvent> adapt(VarbitChanged event)
	{
		Objects.requireNonNull(event, "event");
		return adaptVarp(event.getVarpId(), event.getValue());
	}

	Optional<StatusChangedEvent> adaptVarp(int varpId, int value)
	{
		if (varpId != VarPlayerID.POISON)
		{
			return Optional.empty();
		}
		return Optional.of(fromVarp(value));
	}

	public StatusChangedEvent fromVarp(int value)
	{
		StatusChangedEvent.Status status;
		if (value <= 0)
		{
			status = StatusChangedEvent.Status.CLEAR;
		}
		else if (value >= VENOM_THRESHOLD)
		{
			status = StatusChangedEvent.Status.VENOMED;
		}
		else
		{
			status = StatusChangedEvent.Status.POISONED;
		}
		return new StatusChangedEvent(SOURCE, status);
	}
}
