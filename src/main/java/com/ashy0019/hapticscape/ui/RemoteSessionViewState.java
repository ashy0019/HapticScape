package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;

/** Pure presentation state derived from the authoritative remote-session snapshot. */
final class RemoteSessionViewState
{
	private final boolean local;
	private final boolean waiting;
	private final boolean sessionHeader;
	private final boolean controllerDashboard;
	private final boolean participantPermissions;
	private final boolean emergency;
	private final boolean resume;
	private final boolean end;

	private RemoteSessionViewState(
		boolean local,
		boolean waiting,
		boolean sessionHeader,
		boolean controllerDashboard,
		boolean participantPermissions,
		boolean emergency,
		boolean resume,
		boolean end)
	{
		this.local = local;
		this.waiting = waiting;
		this.sessionHeader = sessionHeader;
		this.controllerDashboard = controllerDashboard;
		this.participantPermissions = participantPermissions;
		this.emergency = emergency;
		this.resume = resume;
		this.end = end;
	}

	static RemoteSessionViewState from(RemoteSessionSnapshot snapshot)
	{
		RemoteSessionState state = snapshot.getState();
		boolean local = state == RemoteSessionState.LOCAL;
		boolean waiting = RemotePairingPanel.viewFor(snapshot)
			== RemotePairingPanel.PairingView.WAITING;
		boolean participant = snapshot.getRole() == RemoteRole.PARTICIPANT && !local;
		boolean controller = snapshot.getRole() == RemoteRole.CONTROLLER && !local;
		boolean participantActive = participant && state == RemoteSessionState.ACTIVE;
		boolean participantPaused = participant
			&& state == RemoteSessionState.EMERGENCY_PAUSED;
		boolean controllerReady = controller
			&& (state == RemoteSessionState.ACTIVE
				|| state == RemoteSessionState.PEER_EMERGENCY_PAUSED);
		return new RemoteSessionViewState(
			local,
			waiting,
			!local && !waiting,
			controllerReady,
			local || participantActive || participantPaused,
			participantActive,
			participantPaused,
			!local && !waiting
		);
	}

	boolean isLocal()
	{
		return local;
	}

	boolean isWaiting()
	{
		return waiting;
	}

	boolean showsSessionHeader()
	{
		return sessionHeader;
	}

	boolean showsControllerDashboard()
	{
		return controllerDashboard;
	}

	boolean showsParticipantPermissions()
	{
		return participantPermissions;
	}

	boolean showsEmergency()
	{
		return emergency;
	}

	boolean showsResume()
	{
		return resume;
	}

	boolean showsEnd()
	{
		return end;
	}
}
