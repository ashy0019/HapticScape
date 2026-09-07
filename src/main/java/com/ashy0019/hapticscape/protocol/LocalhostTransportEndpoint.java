package com.ashy0019.hapticscape.protocol;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Stable loopback endpoint for local gameplay-event sources. */
public final class LocalhostTransportEndpoint
{
	public static final String HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 41713;
	public static final int CONNECT_TIMEOUT_MILLIS = 1_500;
	public static final int HANDSHAKE_TIMEOUT_MILLIS = 3_000;
	public static final int MAX_FRAME_BYTES = 524_288;

	private LocalhostTransportEndpoint()
	{
	}

	public static InetAddress address()
	{
		try
		{
			return InetAddress.getByName(HOST);
		}
		catch (UnknownHostException ex)
		{
			throw new IllegalStateException("IPv4 loopback address is unavailable", ex);
		}
	}
}
