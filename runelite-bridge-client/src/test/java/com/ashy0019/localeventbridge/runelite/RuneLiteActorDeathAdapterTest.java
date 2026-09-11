package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.ActorDeathObservation;
import java.lang.reflect.Proxy;
import java.util.Optional;
import net.runelite.api.Actor;
import net.runelite.api.events.ActorDeath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteActorDeathAdapterTest
{
	private final RuneLiteActorDeathAdapter adapter =
		new RuneLiteActorDeathAdapter();

	@Test
	public void emitsOnlyForTheLocalPlayer()
	{
		Actor localPlayer = actor();
		Actor otherActor = actor();

		Optional<ActorDeathObservation> localDeath = adapter.adapt(
			new ActorDeath(localPlayer),
			localPlayer
		);

		assertTrue(localDeath.isPresent());
		assertEquals("runelite", localDeath.get().getSource());
		assertEquals(ActorDeathObservation.TYPE, localDeath.get().getType());
		assertFalse(adapter.adapt(new ActorDeath(otherActor), localPlayer).isPresent());
		assertFalse(adapter.adapt(new ActorDeath(localPlayer), null).isPresent());
	}

	private static Actor actor()
	{
		return (Actor) Proxy.newProxyInstance(
			Actor.class.getClassLoader(),
			new Class<?>[] { Actor.class },
			(proxy, method, arguments) -> null
		);
	}
}
