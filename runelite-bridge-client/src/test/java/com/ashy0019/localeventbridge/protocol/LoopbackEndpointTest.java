package com.ashy0019.localeventbridge.protocol;

import java.net.InetAddress;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoopbackEndpointTest
{
	@Test
	public void productionEndpointIsFixedIpv4Loopback()
	{
		assertEquals("127.0.0.1", LoopbackEndpoint.HOST);
		assertEquals(41713, LoopbackEndpoint.DEFAULT_PORT);

		InetAddress address = LoopbackEndpoint.address();
		assertTrue(address.isLoopbackAddress());
		assertEquals("127.0.0.1", address.getHostAddress());
	}
}
