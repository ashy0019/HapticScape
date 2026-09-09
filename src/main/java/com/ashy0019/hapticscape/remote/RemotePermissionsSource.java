package com.ashy0019.hapticscape.remote;

/** Source-neutral view of participant-owned Remote Play safety permissions. */
public interface RemotePermissionsSource
{
	boolean remoteSettingsAllowed();
	boolean remoteHapticsAllowed();
	boolean remoteLiveHapticsAllowed();
	boolean remoteClicksAllowed();
	boolean remoteDesktopNotificationsAllowed();
	boolean remoteLocalChatboxMessagesAllowed();
	boolean remoteProtectedExitAllowed();
	int remoteMaximumIntensityPercent();
	int remoteMaximumDurationMillis();
	int remoteMaximumLiveDurationMillis();
}
