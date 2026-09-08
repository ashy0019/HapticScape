package com.ashy0019.hapticscape.protocol;

/** Stable identifiers for the game-agnostic localhost source transport. */
public final class TransportProtocol
{
	public static final String NAME = "hapticscape-local-source";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 131_072;

	private TransportProtocol()
	{
	}
}
