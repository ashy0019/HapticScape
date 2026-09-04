package com.ashy0019.hapticscape.remote;

import java.util.Objects;

public final class RemoteLockSnapshot
{
	private final RemoteLockState state;
	private final String message;

	RemoteLockSnapshot(RemoteLockState state, String message)
	{
		this.state = Objects.requireNonNull(state, "state");
		this.message = message == null ? "" : message;
	}

	public static RemoteLockSnapshot inactive()
	{
		return new RemoteLockSnapshot(RemoteLockState.INACTIVE, "No post-session lock requested");
	}

	public RemoteLockState getState()
	{
		return state;
	}

	public String getMessage()
	{
		return message;
	}
}
