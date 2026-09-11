package com.ashy0019.localeventbridge.event;

import java.util.Objects;

/**
 * Source-neutral toxic-status observation. Source integrations decode their
 * own native representation into a stable local status.
 */
public final class StatusChangedEvent implements LocalEvent
{
	public static final String TYPE = "status.changed";

	public enum Status
	{
		CLEAR,
		POISONED,
		VENOMED
	}

	private final String source;
	private final Status status;

	public StatusChangedEvent(String source, Status status)
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
