package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.DiscordLinkState;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemotePairingWorkspaceTest
{
	@Test
	public void pairingViewFollowsTheSessionWorkflow()
	{
		assertEquals(
			RemotePairingPanel.PairingView.CONNECT,
			RemotePairingPanel.viewFor(RemoteSessionSnapshot.local())
		);
		assertEquals(
			RemotePairingPanel.PairingView.WAITING,
			RemotePairingPanel.viewFor(snapshot(
				RemoteRole.CONTROLLER,
				RemoteSessionState.CONNECTING
			))
		);
		assertEquals(
			RemotePairingPanel.PairingView.WAITING,
			RemotePairingPanel.viewFor(snapshot(
				RemoteRole.CONTROLLER,
				RemoteSessionState.WAITING_FOR_PEER
			))
		);
		assertEquals(
			RemotePairingPanel.PairingView.HIDDEN,
			RemotePairingPanel.viewFor(snapshot(
				RemoteRole.CONTROLLER,
				RemoteSessionState.ACTIVE
			))
		);
		assertEquals(
			RemotePairingPanel.PairingView.HIDDEN,
			RemotePairingPanel.viewFor(snapshot(
				RemoteRole.PARTICIPANT,
				RemoteSessionState.WAITING_FOR_SETTINGS
			))
		);
	}

	@Test
	public void discordSetupCollapsesAfterLinking()
	{
		assertTrue(RemotePairingPanel.showsDiscordSetup(DiscordLinkState.UNLINKED));
		assertFalse(RemotePairingPanel.showsDiscordSetup(DiscordLinkState.CONNECTING));
		assertFalse(RemotePairingPanel.showsDiscordSetup(DiscordLinkState.LINKED));
		assertFalse(RemotePairingPanel.showsDiscordSetup(DiscordLinkState.OFFLINE));

		assertFalse(RemotePairingPanel.showsDiscordUnlink(DiscordLinkState.UNLINKED));
		assertTrue(RemotePairingPanel.showsDiscordUnlink(DiscordLinkState.LINKED));
		assertTrue(RemotePairingPanel.showsDiscordUnlink(DiscordLinkState.OFFLINE));
	}

	private static RemoteSessionSnapshot snapshot(
		RemoteRole role,
		RemoteSessionState state)
	{
		return new RemoteSessionSnapshot(role, state, state.name(), 0);
	}
}
