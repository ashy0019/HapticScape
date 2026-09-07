package com.ashy0019.hapticscape.protocol;

/** Raised when a transport frame is malformed or incompatible. */
public final class TransportProtocolException extends RuntimeException
{
	public TransportProtocolException(String message)
	{
		super(message);
	}

	public TransportProtocolException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
