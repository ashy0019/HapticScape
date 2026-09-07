package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RuneLiteToxicStatusEventAdapterTest
{
	private final RuneLiteToxicStatusEventAdapter adapter = new RuneLiteToxicStatusEventAdapter();

	@Test
	public void decodesRuneScapePoisonVarpIntoNeutralStatus()
	{
		assertStatus(ToxicStatusChangedEvent.Status.CLEAR, 0);
		assertStatus(ToxicStatusChangedEvent.Status.CLEAR, -10);
		assertStatus(ToxicStatusChangedEvent.Status.POISONED, 1);
		assertStatus(ToxicStatusChangedEvent.Status.POISONED, 100);
		assertStatus(ToxicStatusChangedEvent.Status.VENOMED, 1_000_000);
		assertStatus(ToxicStatusChangedEvent.Status.VENOMED, 1_000_005);
	}

	@Test
	public void ignoresUnrelatedVarps()
	{
		assertFalse(adapter.adaptVarp(VarPlayerID.SA_ENERGY, 500).isPresent());
	}

	private void assertStatus(ToxicStatusChangedEvent.Status expected, int rawValue)
	{
		ToxicStatusChangedEvent event = adapter.adaptVarp(VarPlayerID.POISON, rawValue).get();
		assertEquals("runelite", event.getSource());
		assertEquals(ToxicStatusChangedEvent.TYPE, event.getType());
		assertEquals(expected, event.getStatus());
	}
}
