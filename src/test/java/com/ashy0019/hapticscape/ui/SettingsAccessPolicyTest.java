package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SettingsAccessPolicyTest
{
	@Test
	public void endingLockedParticipantSessionOpensForgeAndMusicOnly()
	{
		SettingsAccessPolicy.Mode duringSession = SettingsAccessPolicy.resolve(
			snapshot(RemoteRole.PARTICIPANT, RemoteSessionState.ACTIVE),
			false,
			false,
			true
		);
		SettingsAccessPolicy.Mode afterSession = SettingsAccessPolicy.resolve(
			RemoteSessionSnapshot.local(),
			false,
			false,
			true
		);

		assertSame(SettingsAccessPolicy.Mode.REMOTE_READ_ONLY, duringSession);
		assertTrue(duringSession.isFeedbackReadOnly());
		assertTrue(duringSession.areForgeAndMusicReadOnly());
		assertSame(SettingsAccessPolicy.Mode.POST_SESSION_LOCK, afterSession);
		assertTrue(afterSession.isFeedbackReadOnly());
		assertFalse(afterSession.areForgeAndMusicReadOnly());
	}

	@Test
	public void unlockedLocalSettingsAreFullyEditable()
	{
		SettingsAccessPolicy.Mode mode = SettingsAccessPolicy.resolve(
			RemoteSessionSnapshot.local(),
			false,
			false,
			false
		);

		assertSame(SettingsAccessPolicy.Mode.EDITABLE, mode);
		assertFalse(mode.isFeedbackReadOnly());
		assertFalse(mode.areForgeAndMusicReadOnly());
	}

	@Test
	public void controllerSubjectWorkspaceHonorsPermission()
	{
		RemoteSessionSnapshot active = snapshot(
			RemoteRole.CONTROLLER,
			RemoteSessionState.ACTIVE
		);

		assertSame(
			SettingsAccessPolicy.Mode.EDITABLE,
			SettingsAccessPolicy.resolve(active, true, true, false)
		);
		assertSame(
			SettingsAccessPolicy.Mode.REMOTE_READ_ONLY,
			SettingsAccessPolicy.resolve(active, true, false, false)
		);
	}

	private static RemoteSessionSnapshot snapshot(
		RemoteRole role,
		RemoteSessionState state)
	{
		return new RemoteSessionSnapshot(role, state, "test", 0);
	}
}
