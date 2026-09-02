package com.ashy0019.hapticscape.music;

import java.util.Objects;

public final class MusicSyncSnapshot
{
	public enum State
	{
		DISABLED,
		STARTING,
		RUNNING,
		ERROR
	}

	private final State state;
	private final String message;
	private final int levelPercent;

	public MusicSyncSnapshot(State state, String message, int levelPercent)
	{
		this.state = Objects.requireNonNull(state, "state");
		this.message = Objects.requireNonNull(message, "message");
		this.levelPercent = Math.max(0, Math.min(100, levelPercent));
	}

	public State getState()
	{
		return state;
	}

	public String getMessage()
	{
		return message;
	}

	public int getLevelPercent()
	{
		return levelPercent;
	}

	public static MusicSyncSnapshot disabled()
	{
		return new MusicSyncSnapshot(State.DISABLED, "Music sync is off", 0);
	}
}
