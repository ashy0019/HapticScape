package com.ashy0019.hapticscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class HapticScapeConfigTest
{
	@Test
	public void resolvesBlankRelayValuesToHostedDefault()
	{
		assertEquals(
			"wss://hapticscape-remote-relay.hapticscape.workers.dev/relay",
			HapticScapeConfig.DEFAULT_REMOTE_RELAY_URL
		);
		assertEquals(
			HapticScapeConfig.DEFAULT_REMOTE_RELAY_URL,
			HapticScapeConfig.resolveRemoteRelayUrl(null)
		);
		assertEquals(
			HapticScapeConfig.DEFAULT_REMOTE_RELAY_URL,
			HapticScapeConfig.resolveRemoteRelayUrl("")
		);
		assertEquals(
			HapticScapeConfig.DEFAULT_REMOTE_RELAY_URL,
			HapticScapeConfig.resolveRemoteRelayUrl("   ")
		);
	}

	@Test
	public void preservesCustomRelayValues()
	{
		assertEquals(
			"wss://relay.example/relay",
			HapticScapeConfig.resolveRemoteRelayUrl(
				"  wss://relay.example/relay  "
			)
		);
	}

	@Test
	public void liveForgeRequiresExplicitParticipantOptIn()
	{
		HapticScapeConfig config = new HapticScapeConfig() { };

		assertFalse(config.remoteLiveHapticsAllowed());
		assertEquals(30_000, config.remoteMaximumLiveDurationMillis());
	}
}
