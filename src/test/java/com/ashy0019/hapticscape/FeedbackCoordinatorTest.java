package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedbackCoordinatorTest
{
	@Test
	public void participantOutputIsPausedOutsideActiveAndLocalStates()
	{
		assertTrue(FeedbackCoordinator.isRemoteOutputPaused(snapshot(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.WAITING_FOR_SETTINGS
		)));
		assertTrue(FeedbackCoordinator.isRemoteOutputPaused(snapshot(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.EMERGENCY_PAUSED
		)));
		assertFalse(FeedbackCoordinator.isRemoteOutputPaused(snapshot(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.ACTIVE
		)));
		assertFalse(FeedbackCoordinator.isRemoteOutputPaused(snapshot(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.LOCAL
		)));
	}

	@Test
	public void controllerStateDoesNotPauseLocalOutput()
	{
		assertFalse(FeedbackCoordinator.isRemoteOutputPaused(snapshot(
			RemoteRole.CONTROLLER,
			RemoteSessionState.WAITING_FOR_SETTINGS
		)));
		assertFalse(FeedbackCoordinator.isRemoteOutputPaused(snapshot(
			RemoteRole.NONE,
			RemoteSessionState.LOCAL
		)));
	}

	private static RemoteSessionSnapshot snapshot(
		RemoteRole role,
		RemoteSessionState state)
	{
		return new RemoteSessionSnapshot(role, state, "test", 0);
	}
}
