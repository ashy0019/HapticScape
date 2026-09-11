package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.ResourceChangedEvent;
import java.util.Optional;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteResourceAdapterTest
{
	private final RuneLiteResourceAdapter adapter = new RuneLiteResourceAdapter();

	@Test
	public void mapsHitpointsToNeutralState()
	{
		ResourceChangedEvent event = adapter.adapt(Skill.HITPOINTS, 17, 99).get();

		assertEquals("runelite", event.getSource());
		assertEquals(ResourceChangedEvent.TYPE, event.getType());
		assertEquals(ResourceChangedEvent.Kind.HITPOINTS, event.getKind());
		assertEquals(17, event.getCurrentValue());
		assertEquals(99, event.getMaximumValue());
	}

	@Test
	public void mapsPrayerAndIgnoresUnrelatedSkills()
	{
		Optional<ResourceChangedEvent> prayer = adapter.adapt(Skill.PRAYER, 8, 77);

		assertTrue(prayer.isPresent());
		assertEquals(ResourceChangedEvent.Kind.PRAYER, prayer.get().getKind());
		assertFalse(adapter.adapt(Skill.COOKING, 50, 99).isPresent());
	}

	@Test
	public void mapsSpecialAttackVarpAndClampsToPercentRange()
	{
		ResourceChangedEvent ready = adapter.adaptVarp(VarPlayerID.SA_ENERGY, 1_000).get();
		ResourceChangedEvent over = adapter.adaptVarp(VarPlayerID.SA_ENERGY, 1_500).get();
		ResourceChangedEvent under = adapter.adaptVarp(VarPlayerID.SA_ENERGY, -10).get();

		assertEquals(ResourceChangedEvent.Kind.SPECIAL_ATTACK, ready.getKind());
		assertEquals(100, ready.getCurrentValue());
		assertEquals(100, ready.getMaximumValue());
		assertEquals(100, over.getCurrentValue());
		assertEquals(0, under.getCurrentValue());
	}

	@Test
	public void ignoresOtherVarps()
	{
		assertFalse(adapter.adaptVarp(VarPlayerID.POISON, 100).isPresent());
	}
}
