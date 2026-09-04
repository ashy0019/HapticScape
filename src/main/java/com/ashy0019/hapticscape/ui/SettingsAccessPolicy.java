package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.util.Objects;

/** Resolves the single settings-access mode that the sidebar must display. */
final class SettingsAccessPolicy
{
	enum Mode
	{
		EDITABLE(false, false),
		REMOTE_READ_ONLY(true, true),
		POST_SESSION_LOCK(true, false);

		private final boolean feedbackReadOnly;
		private final boolean forgeAndMusicReadOnly;

		Mode(boolean feedbackReadOnly, boolean forgeAndMusicReadOnly)
		{
			this.feedbackReadOnly = feedbackReadOnly;
			this.forgeAndMusicReadOnly = forgeAndMusicReadOnly;
		}

		boolean isFeedbackReadOnly()
		{
			return feedbackReadOnly;
		}

		boolean areForgeAndMusicReadOnly()
		{
			return forgeAndMusicReadOnly;
		}
	}

	private SettingsAccessPolicy()
	{
	}

	static Mode resolve(
		RemoteSessionSnapshot snapshot,
		boolean subjectWorkspaceActive,
		boolean controllerSettingsAllowed,
		boolean settingsLocked)
	{
		RemoteSessionSnapshot current = Objects.requireNonNull(snapshot, "snapshot");
		if (current.isParticipantControlled())
		{
			return Mode.REMOTE_READ_ONLY;
		}

		if (subjectWorkspaceActive
			&& current.getRole() == RemoteRole.CONTROLLER
			&& current.getState() != RemoteSessionState.LOCAL)
		{
			boolean controllerEditable = controllerSettingsAllowed
				&& (current.getState() == RemoteSessionState.ACTIVE
					|| current.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED);
			return controllerEditable ? Mode.EDITABLE : Mode.REMOTE_READ_ONLY;
		}

		return settingsLocked ? Mode.POST_SESSION_LOCK : Mode.EDITABLE;
	}
}
