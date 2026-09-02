package com.ashy0019.hapticscape.device;

import java.util.Objects;

/**
 * A finite haptic pattern together with the event semantics used to schedule it.
 */
public final class HapticRequest
{
	private final HapticEventType eventType;
	private final HapticPattern pattern;

	public HapticRequest(HapticEventType eventType, HapticPattern pattern)
	{
		this.eventType = Objects.requireNonNull(eventType, "eventType");
		this.pattern = Objects.requireNonNull(pattern, "pattern");
	}

	public HapticEventType getEventType()
	{
		return eventType;
	}

	public HapticPattern getPattern()
	{
		return pattern;
	}

	@Override
	public String toString()
	{
		return eventType.name();
	}
}
