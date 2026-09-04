package com.ashy0019.hapticscape.remote;

import java.util.Objects;

final class InMemoryRemotePermissionsStore implements RemotePermissionsStore
{
	private RemotePermissions permissions;

	InMemoryRemotePermissionsStore(RemotePermissions permissions)
	{
		this.permissions = Objects.requireNonNull(permissions, "permissions");
	}

	@Override
	public synchronized RemotePermissions capture()
	{
		return permissions;
	}

	@Override
	public synchronized RemotePermissions save(RemotePermissions updated)
	{
		updated.validate();
		permissions = updated;
		return permissions;
	}
}
