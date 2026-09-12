package com.ashy0019.hapticscape.protocol;

/** Indicates that an event wire message is unsupported or malformed. */
public final class EventProtocolException extends IllegalArgumentException
{
	public EventProtocolException(String message)
	{
		super(message);
	}

	public EventProtocolException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
