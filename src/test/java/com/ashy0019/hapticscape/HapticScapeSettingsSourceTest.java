package com.ashy0019.hapticscape;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HapticScapeSettingsSourceTest
{
	@Test
	public void resolvesBlankRelayValuesToHostedDefault()
	{
		assertEquals(
			"wss://hapticscape-remote-relay.hapticscape.workers.dev/relay",
			HapticScapeSettingsSource.DEFAULT_REMOTE_RELAY_URL
		);
		assertEquals(
			HapticScapeSettingsSource.DEFAULT_REMOTE_RELAY_URL,
			HapticScapeSettingsSource.resolveRemoteRelayUrl(null)
		);
		assertEquals(
			HapticScapeSettingsSource.DEFAULT_REMOTE_RELAY_URL,
			HapticScapeSettingsSource.resolveRemoteRelayUrl("")
		);
		assertEquals(
			HapticScapeSettingsSource.DEFAULT_REMOTE_RELAY_URL,
			HapticScapeSettingsSource.resolveRemoteRelayUrl("   ")
		);
	}

	@Test
	public void preservesCustomRelayValues()
	{
		assertEquals(
			"wss://relay.example/relay",
			HapticScapeSettingsSource.resolveRemoteRelayUrl(
				"  wss://relay.example/relay  "
			)
		);
	}
}
