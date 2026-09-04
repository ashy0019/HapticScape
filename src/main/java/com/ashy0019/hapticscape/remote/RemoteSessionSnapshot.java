package com.ashy0019.hapticscape.remote;

import java.util.Objects;

public final class RemoteSessionSnapshot
{
	private final RemoteRole role;
	private final RemoteSessionState state;
	private final String message;
	private final long settingsVersion;

	public RemoteSessionSnapshot(
		RemoteRole role,
		RemoteSessionState state,
		String message,
		long settingsVersion)
	{
		this.role = Objects.requireNonNull(role, "role");
		this.state = Objects.requireNonNull(state, "state");
		this.message = message == null ? "" : message;
		this.settingsVersion = Math.max(0, settingsVersion);
	}

	public static RemoteSessionSnapshot local()
	{
		return new RemoteSessionSnapshot(
			RemoteRole.NONE,
			RemoteSessionState.LOCAL,
			"Local control",
			0
		);
	}

	public RemoteRole getRole()
	{
		return role;
	}

	public RemoteSessionState getState()
	{
		return state;
	}

	public String getMessage()
	{
		return message;
	}

	public long getSettingsVersion()
	{
		return settingsVersion;
	}

	public boolean isParticipantControlled()
	{
		return role == RemoteRole.PARTICIPANT && state != RemoteSessionState.LOCAL;
	}

	public boolean isEmergencyPaused()
	{
		return state == RemoteSessionState.EMERGENCY_PAUSED;
	}
}
