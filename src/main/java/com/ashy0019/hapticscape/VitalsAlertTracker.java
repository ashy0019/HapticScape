package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Source-neutral crossing policy for HP, prayer and special-attack state.
 * Integrations report values; HapticScape owns thresholds and rearming.
 */
public final class VitalsAlertTracker
{
	private final Map<StateKey, Integer> previousValues = new HashMap<>();

	public void seed(VitalsChangedEvent event)
	{
		VitalsChangedEvent required = Objects.requireNonNull(event, "event");
		previousValues.put(StateKey.of(required), required.getCurrentValue());
	}

	public Optional<AlertCategory> update(
		VitalsChangedEvent event,
		AlertTriggerSettings settings)
	{
		VitalsChangedEvent required = Objects.requireNonNull(event, "event");
		AlertTriggerSettings requiredSettings = Objects.requireNonNull(settings, "settings");
		AlertCategory category = categoryFor(required.getKind());
		StateKey key = StateKey.of(required);
		Integer previous = previousValues.put(key, required.getCurrentValue());
		if (previous != null && category.getTriggerParameter().crossed(
			previous,
			required.getCurrentValue(),
			requiredSettings.get(category)
		))
		{
			return Optional.of(category);
		}
		return Optional.empty();
	}

	public void reset()
	{
		previousValues.clear();
	}

	private static AlertCategory categoryFor(VitalsChangedEvent.Kind kind)
	{
		switch (Objects.requireNonNull(kind, "kind"))
		{
			case HITPOINTS:
				return AlertCategory.LOW_HITPOINTS;
			case PRAYER:
				return AlertCategory.LOW_PRAYER;
			case SPECIAL_ATTACK:
				return AlertCategory.SPECIAL_ATTACK_READY;
			default:
				throw new IllegalArgumentException("Unsupported vital kind: " + kind);
		}
	}

	private static final class StateKey
	{
		private final String source;
		private final VitalsChangedEvent.Kind kind;

		private StateKey(String source, VitalsChangedEvent.Kind kind)
		{
			this.source = source;
			this.kind = kind;
		}

		private static StateKey of(VitalsChangedEvent event)
		{
			return new StateKey(event.getSource(), event.getKind());
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof StateKey))
			{
				return false;
			}
			StateKey that = (StateKey) other;
			return source.equals(that.source) && kind == that.kind;
		}

		@Override
		public int hashCode()
		{
			return 31 * source.hashCode() + kind.hashCode();
		}
	}
}
