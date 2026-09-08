package com.ashy0019.hapticscape.protocol;

/** Stable identifiers for the game-agnostic localhost source transport. */
public final class TransportProtocol
{
	public static final String NAME = "local-event-bridge";
	public static final String LEGACY_NAME = "hapticscape-local-source";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 131_072;

	public static boolean supports(String name)
	{
		return NAME.equals(name) || LEGACY_NAME.equals(name);
	}

	private TransportProtocol()
	{
	}
}
