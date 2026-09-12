package com.ashy0019.hapticscape.protocol;

/** Stable identifiers for the source-neutral gameplay event wire protocol. */
public final class EventProtocol
{
	public static final String NAME = "local-event-bridge-events";
	public static final String LEGACY_NAME = "hapticscape-local-events";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 65_536;

	public static boolean supports(String name)
	{
		return NAME.equals(name) || LEGACY_NAME.equals(name);
	}

	private EventProtocol()
	{
	}
}
