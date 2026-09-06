package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Actor;
import net.runelite.api.events.ActorDeath;

/** Translates RuneLite actor deaths into local-player death observations. */
public final class RuneLitePlayerDeathEventAdapter
{
	private static final String SOURCE = "runelite";

	public Optional<PlayerDeathEvent> adapt(ActorDeath event, Actor localPlayer)
	{
		Objects.requireNonNull(event, "event");
		if (localPlayer == null || event.getActor() != localPlayer)
		{
			return Optional.empty();
		}
		return Optional.of(new PlayerDeathEvent(SOURCE));
	}
}
