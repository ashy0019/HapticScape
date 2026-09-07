package com.ashy0019.hapticscape.event;

import java.util.Objects;

/**
 * Source-neutral observation that an integration emitted a user notification.
 * The integration reports focus state and source policy facts; HapticScape
 * decides whether any configured output should be produced.
 */
public final class NotificationEvent implements HapticScapeEvent
{
	public static final String TYPE = "notification";

	private final String source;
	private final boolean sourceFocused;
	private final boolean sendWhenFocused;

	public NotificationEvent(String source, boolean sourceFocused, boolean sendWhenFocused)
	{
		this.source = requireIdentifier(source, "source");
		this.sourceFocused = sourceFocused;
		this.sendWhenFocused = sendWhenFocused;
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

	public boolean isSourceFocused()
	{
		return sourceFocused;
	}

	public boolean isSendWhenFocused()
	{
		return sendWhenFocused;
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
