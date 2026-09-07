package com.ashy0019.hapticscape.protocol;

/** Stable identifiers for the localhost gameplay transport protocol. */
public final class TransportProtocol
{
	public static final String NAME = "hapticscape-transport";
	public static final int VERSION = 1;
	public static final int MAX_MESSAGE_CHARS = 131_072;

	private TransportProtocol()
	{
	}
}
