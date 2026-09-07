package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToxicStatusAlertTrackerTest
{
	@Test
	public void alertsWhenNewlyPoisonedAndAgainWhenPoisonBecomesVenom()
	{
		ToxicStatusAlertTracker tracker = new ToxicStatusAlertTracker();
		tracker.seed(event("runelite", ToxicStatusChangedEvent.Status.CLEAR));

		assertTrue(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.POISONED)).isPresent());
		assertFalse(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.POISONED)).isPresent());
		assertTrue(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.VENOMED)).isPresent());
		assertFalse(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.VENOMED)).isPresent());
	}

	@Test
	public void venomDirectlyFromClearAlertsOnce()
	{
		ToxicStatusAlertTracker tracker = new ToxicStatusAlertTracker();
		tracker.seed(event("runelite", ToxicStatusChangedEvent.Status.CLEAR));

		assertTrue(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.VENOMED)).isPresent());
		assertFalse(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.VENOMED)).isPresent());
	}

	@Test
	public void clearingRearmsAndSourcesStayIndependent()
	{
		ToxicStatusAlertTracker tracker = new ToxicStatusAlertTracker();
		tracker.seed(event("runelite", ToxicStatusChangedEvent.Status.POISONED));
		tracker.seed(event("other-game", ToxicStatusChangedEvent.Status.CLEAR));

		assertFalse(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.CLEAR)).isPresent());
		assertTrue(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.POISONED)).isPresent());
		assertTrue(tracker.update(event("other-game", ToxicStatusChangedEvent.Status.POISONED)).isPresent());
	}

	@Test
	public void firstObservationDoesNotFire()
	{
		ToxicStatusAlertTracker tracker = new ToxicStatusAlertTracker();
		assertFalse(tracker.update(event("runelite", ToxicStatusChangedEvent.Status.POISONED)).isPresent());
	}

	private static ToxicStatusChangedEvent event(String source, ToxicStatusChangedEvent.Status status)
	{
		return new ToxicStatusChangedEvent(source, status);
	}
}
