package com.ashy0019.hapticscape.remote;

import java.util.Objects;
import java.util.UUID;

/** Stable identity carried inside the encrypted session hello. */
final class RemoteHello
{
	private final String role;
	private final String clientId;

	RemoteHello(RemoteRole role, String clientId)
	{
		this.role = Objects.requireNonNull(role, "role").name();
		this.clientId = Objects.requireNonNull(clientId, "clientId");
	}

	RemoteRole getRole()
	{
		return RemoteRole.valueOf(Objects.requireNonNull(role, "role"));
	}

	String getClientId()
	{
		UUID.fromString(Objects.requireNonNull(clientId, "clientId"));
		return clientId;
	}
}
