package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.StatusChangedEvent;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RuneLiteStatusAdapterTest
{
	private final RuneLiteStatusAdapter adapter = new RuneLiteStatusAdapter();

	@Test
	public void decodesRuneScapePoisonVarpIntoNeutralStatus()
	{
		assertStatus(StatusChangedEvent.Status.CLEAR, 0);
		assertStatus(StatusChangedEvent.Status.CLEAR, -10);
		assertStatus(StatusChangedEvent.Status.POISONED, 1);
		assertStatus(StatusChangedEvent.Status.POISONED, 100);
		assertStatus(StatusChangedEvent.Status.VENOMED, 1_000_000);
		assertStatus(StatusChangedEvent.Status.VENOMED, 1_000_005);
	}

	@Test
	public void ignoresUnrelatedVarps()
	{
		assertFalse(adapter.adaptVarp(VarPlayerID.SA_ENERGY, 500).isPresent());
	}

	private void assertStatus(StatusChangedEvent.Status expected, int rawValue)
	{
		StatusChangedEvent event = adapter.adaptVarp(VarPlayerID.POISON, rawValue).get();
		assertEquals("runelite", event.getSource());
		assertEquals(StatusChangedEvent.TYPE, event.getType());
		assertEquals(expected, event.getStatus());
	}
}
