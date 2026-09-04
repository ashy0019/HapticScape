package com.ashy0019.hapticscape.remote;

public interface RemotePermissionsStore
{
	RemotePermissions capture();

	RemotePermissions save(RemotePermissions permissions);
}
