package com.ashy0019.localeventbridge;

import java.util.Locale;
import java.util.Objects;

/** Canonical identifiers used by source-neutral local events. */
public final class EventIdentifiers
{
	private EventIdentifiers()
	{
	}

	public static String canonical(String value)
	{
		String normalized = Objects.requireNonNull(value, "value")
			.trim()
			.toLowerCase(Locale.ROOT);
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException("identifier must not be empty");
		}
		if (normalized.indexOf(',') >= 0 || normalized.indexOf(';') >= 0)
		{
			throw new IllegalArgumentException("identifier contains a reserved delimiter");
		}
		return normalized;
	}
}
