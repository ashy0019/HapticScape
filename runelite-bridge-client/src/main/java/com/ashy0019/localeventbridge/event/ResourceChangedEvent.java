package com.ashy0019.localeventbridge.event;

import java.util.Objects;

/**
 * Source-neutral observation of a gameplay resource. Source-specific resource identifiers and event classes are translated
 * before this event crosses into core logic.
 */
public final class ResourceChangedEvent implements LocalEvent
{
	public static final String TYPE = "resource.changed";

	public enum Kind
	{
		HITPOINTS,
		PRAYER,
		SPECIAL_ATTACK
	}

	private final String source;
	private final Kind kind;
	private final int currentValue;
	private final int maximumValue;

	public ResourceChangedEvent(
		String source,
		Kind kind,
		int currentValue,
		int maximumValue)
	{
		this.source = requireIdentifier(source, "source");
		this.kind = Objects.requireNonNull(kind, "kind");
		if (maximumValue <= 0)
		{
			throw new IllegalArgumentException("maximumValue must be positive");
		}
		this.currentValue = Math.max(0, currentValue);
		this.maximumValue = maximumValue;
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

	public Kind getKind()
	{
		return kind;
	}

	public int getCurrentValue()
	{
		return currentValue;
	}

	public int getMaximumValue()
	{
		return maximumValue;
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
