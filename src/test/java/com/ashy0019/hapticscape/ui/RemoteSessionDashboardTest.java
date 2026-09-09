package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteSessionDashboardTest
{
	@Test
	public void sessionHeaderTitlesDescribeRoleAndSafetyState()
	{
		assertEquals("Securing session...", title(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.WAITING_FOR_SETTINGS
		));
		assertEquals("You are being controlled", title(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.ACTIVE
		));
		assertEquals("Remote control paused", title(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.EMERGENCY_PAUSED
		));
		assertEquals("Controlling partner", title(
			RemoteRole.CONTROLLER,
			RemoteSessionState.ACTIVE
		));
		assertEquals("Partner paused remote control", title(
			RemoteRole.CONTROLLER,
			RemoteSessionState.PEER_EMERGENCY_PAUSED
		));
		assertEquals("Session disconnected", title(
			RemoteRole.CONTROLLER,
			RemoteSessionState.DISCONNECTED
		));
	}

	@Test
	public void participantOwnsSafetyAndPermissionsDuringAnActiveSession()
	{
		RemoteSessionViewState active = view(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.ACTIVE
		);
		assertTrue(active.showsSessionHeader());
		assertTrue(active.showsParticipantPermissions());
		assertTrue(active.showsEmergency());
		assertFalse(active.showsResume());
		assertTrue(active.showsEnd());
		assertFalse(active.showsControllerDashboard());

		RemoteSessionViewState paused = view(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.EMERGENCY_PAUSED
		);
		assertFalse(paused.showsEmergency());
		assertTrue(paused.showsResume());
		assertTrue(paused.showsParticipantPermissions());
	}

	@Test
	public void controllerDashboardPersistsButDisablesThroughPeerPause()
	{
		RemoteSessionViewState active = view(
			RemoteRole.CONTROLLER,
			RemoteSessionState.ACTIVE
		);
		RemoteSessionViewState paused = view(
			RemoteRole.CONTROLLER,
			RemoteSessionState.PEER_EMERGENCY_PAUSED
		);
		assertTrue(active.showsControllerDashboard());
		assertTrue(paused.showsControllerDashboard());
		assertFalse(active.showsParticipantPermissions());
		assertFalse(paused.showsEmergency());
		assertTrue(paused.showsEnd());
	}

	@Test
	public void setupAndLocalViewsDoNotLeakSessionTools()
	{
		RemoteSessionViewState local = RemoteSessionViewState.from(
			RemoteSessionSnapshot.local()
		);
		assertTrue(local.isLocal());
		assertFalse(local.showsSessionHeader());
		assertFalse(local.showsControllerDashboard());
		assertTrue(local.showsParticipantPermissions());
		assertFalse(local.showsEnd());

		RemoteSessionViewState securing = view(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.WAITING_FOR_SETTINGS
		);
		assertTrue(securing.showsSessionHeader());
		assertFalse(securing.showsParticipantPermissions());
		assertFalse(securing.showsEmergency());
		assertTrue(securing.showsEnd());

		RemoteSessionViewState waiting = view(
			RemoteRole.CONTROLLER,
			RemoteSessionState.WAITING_FOR_PEER
		);
		assertTrue(waiting.isWaiting());
		assertFalse(waiting.showsSessionHeader());
		assertFalse(waiting.showsControllerDashboard());
		assertFalse(waiting.showsEnd());
	}

	@Test
	public void activeSessionLayoutsStackAtCompactWidths()
	{
		assertEquals(1, RemoteSessionHeaderPanel.layoutModeForWidth(679));
		assertEquals(2, RemoteSessionHeaderPanel.layoutModeForWidth(680));
		assertEquals(1, RemoteControllerDashboardPanel.layoutModeForWidth(899));
		assertEquals(2, RemoteControllerDashboardPanel.layoutModeForWidth(900));
		assertEquals(1, RemoteActionWorkspacePanel.layoutModeForWidth(679));
		assertEquals(2, RemoteActionWorkspacePanel.layoutModeForWidth(680));
	}

	@Test
	public void controllerPermissionSummaryExposesCurrentLimitsWithoutEditingThem()
	{
		RemotePermissions permissions = new RemotePermissions(
			true,
			true,
			true,
			true,
			true,
			false,
			60,
			3_000,
			30_000
		);
		assertEquals(
			"Settings: Allowed\n"
				+ "Haptics: Up to 60% for 3 s\n"
				+ "Live Forge: Up to 30 s\n"
				+ "Clicks: Allowed\n"
				+ "Messages: Desktop only\n"
				+ "Protected startup/exit: Blocked",
			RemotePermissionSummaryPanel.describe(permissions)
		);

		RemotePermissions blocked = new RemotePermissions(
			false,
			false,
			false,
			false,
			false,
			false,
			0,
			50,
			0
		);
		assertEquals(
			"Settings: Blocked\n"
				+ "Haptics: Blocked\n"
				+ "Live Forge: Blocked\n"
				+ "Clicks: Blocked\n"
				+ "Messages: Blocked\n"
				+ "Protected startup/exit: Blocked",
			RemotePermissionSummaryPanel.describe(blocked)
		);
	}

	private static String title(RemoteRole role, RemoteSessionState state)
	{
		return RemoteSessionHeaderPanel.titleFor(snapshot(role, state));
	}

	private static RemoteSessionViewState view(
		RemoteRole role,
		RemoteSessionState state)
	{
		return RemoteSessionViewState.from(snapshot(role, state));
	}

	private static RemoteSessionSnapshot snapshot(
		RemoteRole role,
		RemoteSessionState state)
	{
		return new RemoteSessionSnapshot(role, state, state.name(), 0);
	}
}
