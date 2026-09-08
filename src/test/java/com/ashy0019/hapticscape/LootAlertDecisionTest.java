package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.LootReceivedEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootAlertDecisionTest
{
	@Test
	public void alertsAtOrAboveConfiguredValue()
	{
		assertFalse(LootAlertDecision.shouldAlert(event(99_999), 100_000));
		assertTrue(LootAlertDecision.shouldAlert(event(100_000), 100_000));
		assertTrue(LootAlertDecision.shouldAlert(event(2_500_000), 100_000));
	}

	@Test
	public void emptyLootBatchNeverAlerts()
	{
		assertFalse(LootAlertDecision.shouldAlert(
			new LootReceivedEvent("test-source", 0, 0L),
			0L
		));
	}

	@Test
	public void neutralEventDoesNotOwnTheThreshold()
	{
		LootReceivedEvent event = event(500_000);

		assertTrue(LootAlertDecision.shouldAlert(event, 100_000));
		assertFalse(LootAlertDecision.shouldAlert(event, 1_000_000));
	}

	private static LootReceivedEvent event(long totalValue)
	{
		return new LootReceivedEvent("test-source", 1, totalValue);
	}
}
