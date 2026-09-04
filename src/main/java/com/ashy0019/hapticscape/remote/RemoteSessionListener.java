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
}
