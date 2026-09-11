package com.ashy0019.localeventbridge.protocol;

/** Stable identifiers for the game-agnostic localhost source transport. */
public final class TransportProtocol
{
	public static final String NAME = "local-event-bridge";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 131_072;

	private TransportProtocol()
	{
	}
}
