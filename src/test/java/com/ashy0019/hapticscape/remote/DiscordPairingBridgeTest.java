package com.ashy0019.hapticscape.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiscordPairingBridgeTest
{
	@Test
	public void pairingCanReplaceOnlyInactiveOrDisconnectedSessions()
	{
		assertTrue(DiscordPairingBridge.canReplaceSession(RemoteSessionState.LOCAL));
		assertTrue(DiscordPairingBridge.canReplaceSession(RemoteSessionState.DISCONNECTED));
		assertFalse(DiscordPairingBridge.canReplaceSession(RemoteSessionState.CONNECTING));
		assertFalse(DiscordPairingBridge.canReplaceSession(RemoteSessionState.ACTIVE));
		assertFalse(DiscordPairingBridge.canReplaceSession(
			RemoteSessionState.PEER_EMERGENCY_PAUSED
		));
	}
}
