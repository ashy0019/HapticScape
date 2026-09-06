package com.ashy0019.hapticscape.remote;

import java.util.Objects;

public final class DiscordLinkSnapshot
{
	private final DiscordLinkState state;
	private final String displayName;
	private final String message;

	DiscordLinkSnapshot(
		DiscordLinkState state,
		String displayName,
		String message)
	{
		this.state = Objects.requireNonNull(state, "state");
		this.displayName = displayName == null ? "" : displayName;
		this.message = Objects.requireNonNull(message, "message");
	}

	public DiscordLinkState getState()
	{
		return state;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getMessage()
	{
		return message;
	}

	public boolean isLinked()
	{
		return state == DiscordLinkState.CONNECTING
			|| state == DiscordLinkState.LINKED
			|| state == DiscordLinkState.OFFLINE;
	}
}
