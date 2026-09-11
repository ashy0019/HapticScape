package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.ActorDeathObservation;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Actor;
import net.runelite.api.events.ActorDeath;

/** Translates RuneLite actor deaths into local-player death observations. */
public final class RuneLiteActorDeathAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;

	public Optional<ActorDeathObservation> adapt(ActorDeath event, Actor localPlayer)
	{
		Objects.requireNonNull(event, "event");
		if (localPlayer == null || event.getActor() != localPlayer)
		{
			return Optional.empty();
		}
		return Optional.of(new ActorDeathObservation(SOURCE));
	}
}
