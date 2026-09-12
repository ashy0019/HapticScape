package com.ashy0019.hapticscape.remote;

public interface RemoteSessionListener
{
	void onRemoteSessionChanged(RemoteSessionSnapshot snapshot);

	default void onRemoteSettingsChanged(RemoteSettingsSnapshot settings)
	{
	}

	default void onRemoteLockChanged(RemoteLockSnapshot lock)
	{
	}

	default void onRemoteLockProposal(SettingsLockProposal proposal)
	{
	}


	default void onRemoteLockNamingRequired(String currentProfileName)
	{
	}

	default void onRemotePermissionsChanged(RemotePermissions permissions)
	{
	}

	default void onRemoteActionAcknowledged(RemoteActionAcknowledgement acknowledgement)
	{
	}

	default void onRemoteActivity(RemoteActivityEvent event)
	{
	}

	default void onUnauthorizedEnd(String reason)
	{
	}
}
