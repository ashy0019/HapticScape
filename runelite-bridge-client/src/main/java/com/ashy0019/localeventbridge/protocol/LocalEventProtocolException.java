package com.ashy0019.localeventbridge.protocol;

/** Indicates that an event wire message is unsupported or malformed. */
public final class LocalEventProtocolException extends IllegalArgumentException
{
	public LocalEventProtocolException(String message)
	{
		super(message);
	}

	public LocalEventProtocolException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
