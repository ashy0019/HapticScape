package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VitalsAlertTrackerTest
{
	private static final String SOURCE = "runelite";

	@Test
	public void firesOnlyWhenCrossingDownwardThroughHitpointsThreshold()
	{
		VitalsAlertTracker tracker = new VitalsAlertTracker();
		tracker.seed(event(SOURCE, VitalsChangedEvent.Kind.HITPOINTS, 40, 99));

		assertFalse(update(tracker, event(SOURCE, VitalsChangedEvent.Kind.HITPOINTS, 30, 99)).isPresent());
		assertEquals(
			AlertCategory.LOW_HITPOINTS,
			update(tracker, event(SOURCE, VitalsChangedEvent.Kind.HITPOINTS, 20, 99)).get()
		);
		assertFalse(update(tracker, event(SOURCE, VitalsChangedEvent.Kind.HITPOINTS, 15, 99)).isPresent());
	}

	@Test
	public void prayerRearmsAfterRecoveringAboveThreshold()
	{
		VitalsAlertTracker tracker = new VitalsAlertTracker();
		tracker.seed(event(SOURCE, VitalsChangedEvent.Kind.PRAYER, 15, 77));

		assertEquals(
			AlertCategory.LOW_PRAYER,
			update(tracker, event(SOURCE, VitalsChangedEvent.Kind.PRAYER, 10, 77)).get()
		);
		assertFalse(update(tracker, event(SOURCE, VitalsChangedEvent.Kind.PRAYER, 9, 77)).isPresent());
		assertFalse(update(tracker, event(SOURCE, VitalsChangedEvent.Kind.PRAYER, 20, 77)).isPresent());
		assertEquals(
			AlertCategory.LOW_PRAYER,
			update(tracker, event(SOURCE, VitalsChangedEvent.Kind.PRAYER, 10, 77)).get()
		);
	}

	@Test
	public void specialAttackFiresUpwardAndRearmsBelowConfiguredThreshold()
	{
		VitalsAlertTracker tracker = new VitalsAlertTracker();
		AlertTriggerSettings settings = AlertTriggerSettings.defaults().withValue(
			AlertCategory.SPECIAL_ATTACK_READY,
			75
		);
		tracker.seed(event(SOURCE, VitalsChangedEvent.Kind.SPECIAL_ATTACK, 60, 100));

		assertFalse(tracker.update(event(SOURCE, VitalsChangedEvent.Kind.SPECIAL_ATTACK, 70, 100), settings).isPresent());
		assertEquals(
			AlertCategory.SPECIAL_ATTACK_READY,
			tracker.update(event(SOURCE, VitalsChangedEvent.Kind.SPECIAL_ATTACK, 75, 100), settings).get()
		);
		assertFalse(tracker.update(event(SOURCE, VitalsChangedEvent.Kind.SPECIAL_ATTACK, 100, 100), settings).isPresent());
		assertFalse(tracker.update(event(SOURCE, VitalsChangedEvent.Kind.SPECIAL_ATTACK, 50, 100), settings).isPresent());
		assertTrue(tracker.update(event(SOURCE, VitalsChangedEvent.Kind.SPECIAL_ATTACK, 80, 100), settings).isPresent());
	}

	@Test
	public void sourcesTrackIndependentCrossingState()
	{
		VitalsAlertTracker tracker = new VitalsAlertTracker();
		tracker.seed(event("runelite", VitalsChangedEvent.Kind.HITPOINTS, 40, 99));
		tracker.seed(event("other-game", VitalsChangedEvent.Kind.HITPOINTS, 10, 100));

		assertTrue(update(tracker, event("runelite", VitalsChangedEvent.Kind.HITPOINTS, 20, 99)).isPresent());
		assertFalse(update(tracker, event("other-game", VitalsChangedEvent.Kind.HITPOINTS, 9, 100)).isPresent());
	}

	private static Optional<AlertCategory> update(
		VitalsAlertTracker tracker,
		VitalsChangedEvent event)
	{
		return tracker.update(event, AlertTriggerSettings.defaults());
	}

	private static VitalsChangedEvent event(
		String source,
		VitalsChangedEvent.Kind kind,
		int currentValue,
		int maximumValue)
	{
		return new VitalsChangedEvent(source, kind, currentValue, maximumValue);
	}
}
