package com.ashy0019.hapticscape.remote;

import java.util.Objects;

/** Display-only details for the participant's authoritative local consent prompt. */
public final class DiscordJoinRequest
{
	private final String controllerName;
	private final String relayUrl;

	public DiscordJoinRequest(String controllerName, String relayUrl)
	{
		this.controllerName = Objects.requireNonNull(controllerName, "controllerName");
		this.relayUrl = Objects.requireNonNull(relayUrl, "relayUrl");
	}

	public String getControllerName()
	{
		return controllerName;
	}

	public String getRelayUrl()
	{
		return relayUrl;
	}
}
