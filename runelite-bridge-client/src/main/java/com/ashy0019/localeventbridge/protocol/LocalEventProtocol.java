package com.ashy0019.localeventbridge.protocol;

/** Stable identifiers for the source-neutral gameplay event wire protocol. */
public final class LocalEventProtocol
{
	public static final String NAME = "local-event-bridge-events";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 65_536;

	private LocalEventProtocol()
	{
	}
}
