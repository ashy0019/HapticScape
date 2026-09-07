package com.ashy0019.hapticscape.protocol;

/** Stable identifiers for the source-neutral gameplay event wire protocol. */
public final class EventProtocol
{
	public static final String NAME = "hapticscape-event";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 65_536;

	private EventProtocol()
	{
	}
}
