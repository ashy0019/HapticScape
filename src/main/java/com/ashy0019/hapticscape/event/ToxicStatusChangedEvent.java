package com.ashy0019.hapticscape.event;

import java.util.Objects;

/**
 * Source-neutral toxic-status observation. Source integrations decode their
 * own native representation into a stable HapticScape status.
 */
public final class ToxicStatusChangedEvent implements HapticScapeEvent
{
	public static final String TYPE = "toxic_status_changed";

	public enum Status
	{
		CLEAR,
		POISONED,
		VENOMED
	}

	private final String source;
	private final Status status;

	public ToxicStatusChangedEvent(String source, Status status)
	{
		this.source = requireIdentifier(source, "source");
		this.status = Objects.requireNonNull(status, "status");
	}

	@Override
	public String getSource()
	{
		return source;
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	public Status getStatus()
	{
		return status;
	}

	private static String requireIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}
}
