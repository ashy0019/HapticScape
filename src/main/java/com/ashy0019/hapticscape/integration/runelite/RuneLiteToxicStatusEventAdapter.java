package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;

/** Translates RuneLite's poison varp encoding into a neutral toxic status. */
public final class RuneLiteToxicStatusEventAdapter
{
	private static final String SOURCE = "runelite";
	private static final int VENOM_THRESHOLD = 1_000_000;

	public Optional<ToxicStatusChangedEvent> adapt(VarbitChanged event)
	{
		Objects.requireNonNull(event, "event");
		return adaptVarp(event.getVarpId(), event.getValue());
	}

	Optional<ToxicStatusChangedEvent> adaptVarp(int varpId, int value)
	{
		if (varpId != VarPlayerID.POISON)
		{
			return Optional.empty();
		}
		return Optional.of(fromVarp(value));
	}

	public ToxicStatusChangedEvent fromVarp(int value)
	{
		ToxicStatusChangedEvent.Status status;
		if (value <= 0)
		{
			status = ToxicStatusChangedEvent.Status.CLEAR;
		}
		else if (value >= VENOM_THRESHOLD)
		{
			status = ToxicStatusChangedEvent.Status.VENOMED;
		}
		else
		{
			status = ToxicStatusChangedEvent.Status.POISONED;
		}
		return new ToxicStatusChangedEvent(SOURCE, status);
	}
}
