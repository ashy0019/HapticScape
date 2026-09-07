package com.ashy0019.hapticscape.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LootReceivedEventTest
{
	@Test
	public void carriesOnlyNeutralLootFacts()
	{
		LootReceivedEvent event = new LootReceivedEvent("runelite", 3, 1_234_567L);

		assertEquals("runelite", event.getSource());
		assertEquals(LootReceivedEvent.TYPE, event.getType());
		assertEquals(3, event.getStackCount());
		assertEquals(1_234_567L, event.getTotalValue());
	}
}
