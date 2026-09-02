package com.ashy0019.hapticscape.device;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HapticArbiterTest
{
	private final AtomicLong clock = new AtomicLong();
	private final HapticArbiter arbiter = new HapticArbiter(clock::get);

	@Test
	public void higherPriorityInterruptsLowerPriority()
	{
		HapticRequest xp = request(HapticEventType.XP_GAIN, 0.2);
		HapticRequest directMessage = request(HapticEventType.DIRECT_MESSAGE, 0.8);

		assertEquals(HapticArbiter.Decision.START, arbiter.submit(xp));
		assertEquals(HapticArbiter.Decision.INTERRUPT, arbiter.submit(directMessage));
		assertTrue(arbiter.isActive(directMessage));
		assertFalse(arbiter.isActive(xp));
	}

	@Test
	public void genericNotificationInterruptsRoutineXpButDoesNotQueue()
	{
		HapticRequest xp = request(HapticEventType.XP_GAIN, 0.2);
		HapticRequest generic = request(HapticEventType.GENERIC_NOTIFICATION, 0.5);
		HapticRequest secondGeneric = request(HapticEventType.GENERIC_NOTIFICATION, 0.6);

		arbiter.submit(xp);
		assertEquals(HapticArbiter.Decision.INTERRUPT, arbiter.submit(generic));
		assertEquals(HapticArbiter.Decision.DROP, arbiter.submit(secondGeneric));
		assertTrue(arbiter.isActive(generic));
		assertEquals(0, arbiter.getPendingCount());
	}

	@Test
	public void routineFeedbackCannotInterruptCeremony()
	{
		HapticRequest ceremony = request(HapticEventType.LEVEL_99, 0.7);
		HapticRequest xp = request(HapticEventType.XP_GAIN, 0.3);

		assertEquals(HapticArbiter.Decision.START, arbiter.submit(ceremony));
		assertEquals(HapticArbiter.Decision.DROP, arbiter.submit(xp));
		assertTrue(arbiter.isActive(ceremony));
		assertEquals(0, arbiter.getPendingCount());
	}

	@Test
	public void criticalFeedbackInterruptsCeremony()
	{
		HapticRequest ceremony = request(HapticEventType.LEVEL_99, 0.7);
		HapticRequest lowHitpoints = request(HapticEventType.LOW_HITPOINTS, 1.0);

		arbiter.submit(ceremony);

		assertEquals(HapticArbiter.Decision.INTERRUPT, arbiter.submit(lowHitpoints));
		assertTrue(arbiter.isActive(lowHitpoints));
	}

	@Test
	public void equivalentPendingRequestsCoalesceToLatest()
	{
		HapticRequest ceremony = request(HapticEventType.LEVEL_99, 0.7);
		HapticRequest firstMessage = request(HapticEventType.DIRECT_MESSAGE, 0.4);
		HapticRequest latestMessage = request(HapticEventType.DIRECT_MESSAGE, 0.9);

		arbiter.submit(ceremony);
		assertEquals(HapticArbiter.Decision.QUEUE, arbiter.submit(firstMessage));
		assertEquals(HapticArbiter.Decision.COALESCE, arbiter.submit(latestMessage));
		assertEquals(1, arbiter.getPendingCount());

		assertSame(latestMessage, arbiter.complete(ceremony).get());
	}

	@Test
	public void queuedRequestsRunByPriorityThenArrivalOrder()
	{
		HapticRequest death = request(HapticEventType.PLAYER_DEATH, 1.0);
		HapticRequest levelUp = request(HapticEventType.LEVEL_UP, 0.4);
		HapticRequest valuableDrop = request(HapticEventType.VALUABLE_DROP, 0.6);
		HapticRequest tradeRequest = request(HapticEventType.TRADE_REQUEST, 0.7);
		HapticRequest directMessage = request(HapticEventType.DIRECT_MESSAGE, 0.8);

		arbiter.submit(death);
		arbiter.submit(levelUp);
		arbiter.submit(valuableDrop);
		arbiter.submit(tradeRequest);
		arbiter.submit(directMessage);

		assertSame(tradeRequest, arbiter.complete(death).get());
		assertSame(directMessage, arbiter.complete(tradeRequest).get());
		assertSame(valuableDrop, arbiter.complete(directMessage).get());
		assertSame(levelUp, arbiter.complete(valuableDrop).get());
		assertFalse(arbiter.complete(levelUp).isPresent());
	}

	@Test
	public void staleQueuedRequestIsDiscarded()
	{
		HapticRequest ceremony = request(HapticEventType.LEVEL_99, 0.7);
		HapticRequest directMessage = request(HapticEventType.DIRECT_MESSAGE, 0.8);

		arbiter.submit(ceremony);
		arbiter.submit(directMessage);
		clock.addAndGet(Duration.ofSeconds(5).toNanos());

		assertEquals(Optional.empty(), arbiter.complete(ceremony));
		assertEquals(0, arbiter.getPendingCount());
	}

	@Test
	public void clearRemovesActiveAndPendingRequests()
	{
		HapticRequest ceremony = request(HapticEventType.LEVEL_99, 0.7);
		arbiter.submit(ceremony);
		arbiter.submit(request(HapticEventType.DIRECT_MESSAGE, 0.8));

		arbiter.clear();

		assertFalse(arbiter.hasActiveRequest());
		assertEquals(0, arbiter.getPendingCount());
	}

	private static HapticRequest request(HapticEventType eventType, double intensity)
	{
		return new HapticRequest(
			eventType,
			HapticPattern.single(intensity, Duration.ofMillis(10))
		);
	}
}
