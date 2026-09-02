package com.ashy0019.hapticscape.device;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Thread-confined scheduler for the single finite-pattern output channel.
 */
final class HapticArbiter
{
	static final int MAXIMUM_PENDING_REQUESTS = 8;

	private final LongSupplier nanoClock;
	private final Map<HapticEventType, PendingRequest> pending =
		new EnumMap<>(HapticEventType.class);
	private long sequence;
	private HapticRequest active;

	HapticArbiter()
	{
		this(System::nanoTime);
	}

	HapticArbiter(LongSupplier nanoClock)
	{
		this.nanoClock = nanoClock;
	}

	Decision submit(HapticRequest request)
	{
		long now = nanoClock.getAsLong();
		removeExpired(now);
		if (active == null)
		{
			active = request;
			return Decision.START;
		}

		PlaybackPolicy policy = request.getEventType().getPlaybackPolicy();
		if (policy.interruptsLowerPriority()
			&& hasHigherPriority(request, active))
		{
			active = request;
			return Decision.INTERRUPT;
		}

		if (!policy.queuesWhenBlocked())
		{
			return Decision.DROP;
		}

		HapticEventType eventType = request.getEventType();
		PendingRequest queued = new PendingRequest(request, now, sequence++);
		if (pending.containsKey(eventType))
		{
			pending.put(eventType, queued);
			return Decision.COALESCE;
		}

		if (pending.size() >= MAXIMUM_PENDING_REQUESTS)
		{
			Map.Entry<HapticEventType, PendingRequest> weakest = weakestPending();
			if (weakest == null
				|| !hasHigherPriority(request, weakest.getValue().request))
			{
				return Decision.DROP;
			}
			pending.remove(weakest.getKey());
		}

		pending.put(eventType, queued);
		return Decision.QUEUE;
	}

	Optional<HapticRequest> complete(HapticRequest completed)
	{
		if (active != completed)
		{
			return Optional.empty();
		}

		active = null;
		removeExpired(nanoClock.getAsLong());
		Map.Entry<HapticEventType, PendingRequest> strongest = strongestPending();
		if (strongest == null)
		{
			return Optional.empty();
		}

		PendingRequest next = strongest.getValue();
		pending.remove(strongest.getKey());
		active = next.request;
		return Optional.of(active);
	}

	boolean isActive(HapticRequest request)
	{
		return active == request;
	}

	boolean hasActiveRequest()
	{
		return active != null;
	}

	int getPendingCount()
	{
		removeExpired(nanoClock.getAsLong());
		return pending.size();
	}

	void clear()
	{
		active = null;
		pending.clear();
	}

	private void removeExpired(long now)
	{
		pending.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
	}

	private Map.Entry<HapticEventType, PendingRequest> strongestPending()
	{
		Map.Entry<HapticEventType, PendingRequest> strongest = null;
		for (Map.Entry<HapticEventType, PendingRequest> candidate : pending.entrySet())
		{
			if (strongest == null || isStronger(candidate.getValue(), strongest.getValue()))
			{
				strongest = candidate;
			}
		}
		return strongest;
	}

	private Map.Entry<HapticEventType, PendingRequest> weakestPending()
	{
		Map.Entry<HapticEventType, PendingRequest> weakest = null;
		for (Map.Entry<HapticEventType, PendingRequest> candidate : pending.entrySet())
		{
			if (weakest == null || isStronger(weakest.getValue(), candidate.getValue()))
			{
				weakest = candidate;
			}
		}
		return weakest;
	}

	private static boolean hasHigherPriority(HapticRequest first, HapticRequest second)
	{
		return first.getEventType().getPriority()
			.outranks(second.getEventType().getPriority());
	}

	private static boolean isStronger(PendingRequest first, PendingRequest second)
	{
		int firstPriority = first.request.getEventType().getPriority().getRank();
		int secondPriority = second.request.getEventType().getPriority().getRank();
		return firstPriority > secondPriority
			|| (firstPriority == secondPriority && first.sequence < second.sequence);
	}

	enum Decision
	{
		START,
		INTERRUPT,
		QUEUE,
		COALESCE,
		DROP
	}

	private static final class PendingRequest
	{
		private final HapticRequest request;
		private final long queuedAtNanos;
		private final long sequence;

		private PendingRequest(HapticRequest request, long queuedAtNanos, long sequence)
		{
			this.request = request;
			this.queuedAtNanos = queuedAtNanos;
			this.sequence = sequence;
		}

		private boolean isExpired(long now)
		{
			long maximumAge = request.getEventType().getMaximumQueueAge().toNanos();
			return maximumAge <= 0 || now - queuedAtNanos >= maximumAge;
		}
	}
}
