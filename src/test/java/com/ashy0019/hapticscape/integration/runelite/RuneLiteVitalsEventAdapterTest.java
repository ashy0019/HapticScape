package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import java.util.Optional;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteVitalsEventAdapterTest
{
	private final RuneLiteVitalsEventAdapter adapter = new RuneLiteVitalsEventAdapter();

	@Test
	public void mapsHitpointsToNeutralState()
	{
		VitalsChangedEvent event = adapter.adapt(Skill.HITPOINTS, 17, 99).get();

		assertEquals("runelite", event.getSource());
		assertEquals(VitalsChangedEvent.TYPE, event.getType());
		assertEquals(VitalsChangedEvent.Kind.HITPOINTS, event.getKind());
		assertEquals(17, event.getCurrentValue());
		assertEquals(99, event.getMaximumValue());
	}

	@Test
	public void mapsPrayerAndIgnoresUnrelatedSkills()
	{
		Optional<VitalsChangedEvent> prayer = adapter.adapt(Skill.PRAYER, 8, 77);

		assertTrue(prayer.isPresent());
		assertEquals(VitalsChangedEvent.Kind.PRAYER, prayer.get().getKind());
		assertFalse(adapter.adapt(Skill.COOKING, 50, 99).isPresent());
	}

	@Test
	public void mapsSpecialAttackVarpAndClampsToPercentRange()
	{
		VitalsChangedEvent ready = adapter.adaptVarp(VarPlayerID.SA_ENERGY, 1_000).get();
		VitalsChangedEvent over = adapter.adaptVarp(VarPlayerID.SA_ENERGY, 1_500).get();
		VitalsChangedEvent under = adapter.adaptVarp(VarPlayerID.SA_ENERGY, -10).get();

		assertEquals(VitalsChangedEvent.Kind.SPECIAL_ATTACK, ready.getKind());
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
