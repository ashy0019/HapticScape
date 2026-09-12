package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Source-neutral transition policy for poison/venom alerts. */
public final class ToxicStatusAlertTracker
{
	private final Map<String, ToxicStatusChangedEvent.Status> previousStatus = new HashMap<>();

	public void seed(ToxicStatusChangedEvent event)
	{
		ToxicStatusChangedEvent required = Objects.requireNonNull(event, "event");
		previousStatus.put(required.getSource(), required.getStatus());
	}

	public Optional<AlertCategory> update(ToxicStatusChangedEvent event)
	{
		ToxicStatusChangedEvent required = Objects.requireNonNull(event, "event");
		ToxicStatusChangedEvent.Status current = required.getStatus();
		ToxicStatusChangedEvent.Status previous = previousStatus.put(required.getSource(), current);
		if (previous == null)
		{
			return Optional.empty();
		}

		boolean newlyAffected = previous == ToxicStatusChangedEvent.Status.CLEAR
			&& current != ToxicStatusChangedEvent.Status.CLEAR;
		boolean newlyEnvenomed = previous == ToxicStatusChangedEvent.Status.POISONED
			&& current == ToxicStatusChangedEvent.Status.VENOMED;
		if (newlyAffected || newlyEnvenomed)
		{
			return Optional.of(AlertCategory.POISONED_OR_VENOMED);
		}
		return Optional.empty();
	}

	public void reset()
	{
		previousStatus.clear();
	}
}
