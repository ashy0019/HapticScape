package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import java.util.Objects;

/** Persists participant-owned Remote Control permissions outside remote settings. */
public final class SettingsBackedRemotePermissionsStore implements RemotePermissionsStore
{
	private final RemotePermissionsSource permissionsSource;
	private final SettingsWriter settingsWriter;

	public SettingsBackedRemotePermissionsStore(
		RemotePermissionsSource permissionsSource,
		SettingsWriter settingsWriter)
	{
		this.permissionsSource = Objects.requireNonNull(permissionsSource, "permissionsSource");
		this.settingsWriter = Objects.requireNonNull(settingsWriter, "settingsWriter");
	}

	@Override
	public RemotePermissions capture()
	{
		return RemotePermissions.capture(permissionsSource);
	}

	@Override
	public RemotePermissions save(RemotePermissions permissions)
	{
		RemotePermissions requested = Objects.requireNonNull(permissions, "permissions");
		requested.validate();
		settingsWriter.set(HapticScapeSettingKeys.REMOTE_SETTINGS_ALLOWED, requested.isSettingsAllowed());
		settingsWriter.set(HapticScapeSettingKeys.REMOTE_HAPTICS_ALLOWED, requested.isHapticsAllowed());
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_LIVE_HAPTICS_ALLOWED,
			requested.isLiveHapticsAllowed()
		);
		settingsWriter.set(HapticScapeSettingKeys.REMOTE_CLICKS_ALLOWED, requested.isClicksAllowed());
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_DESKTOP_NOTIFICATIONS_ALLOWED,
			requested.isDesktopNotificationsAllowed()
		);
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_LOCAL_CHATBOX_MESSAGES_ALLOWED,
			requested.isLocalChatboxMessagesAllowed()
		);
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_PROTECTED_EXIT_ALLOWED,
			requested.isProtectedExitAllowed()
		);
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_MAXIMUM_INTENSITY_PERCENT,
			requested.getMaximumIntensityPercent()
		);
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_MAXIMUM_DURATION_MILLIS,
			requested.getMaximumDurationMillis()
		);
		settingsWriter.set(
			HapticScapeSettingKeys.REMOTE_MAXIMUM_LIVE_DURATION_MILLIS,
			requested.getMaximumLiveDurationMillis()
		);
		return requested;
	}

}
