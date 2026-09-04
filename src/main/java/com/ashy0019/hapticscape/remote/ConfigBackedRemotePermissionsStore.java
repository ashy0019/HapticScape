package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import java.util.Objects;
import net.runelite.client.config.ConfigManager;

/** Persists participant-owned Remote Control permissions outside remote settings. */
public final class ConfigBackedRemotePermissionsStore implements RemotePermissionsStore
{
	private final HapticScapeConfig config;
	private final ConfigManager configManager;

	public ConfigBackedRemotePermissionsStore(
		HapticScapeConfig config,
		ConfigManager configManager)
	{
		this.config = Objects.requireNonNull(config, "config");
		this.configManager = Objects.requireNonNull(configManager, "configManager");
	}

	@Override
	public RemotePermissions capture()
	{
		return RemotePermissions.capture(config);
	}

	@Override
	public RemotePermissions save(RemotePermissions permissions)
	{
		RemotePermissions requested = Objects.requireNonNull(permissions, "permissions");
		requested.validate();
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_SETTINGS_ALLOWED_KEY, requested.isSettingsAllowed());
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_HAPTICS_ALLOWED_KEY, requested.isHapticsAllowed());
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_CLICKS_ALLOWED_KEY, requested.isClicksAllowed());
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_DESKTOP_NOTIFICATIONS_ALLOWED_KEY,
			requested.isDesktopNotificationsAllowed());
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_LOCAL_CHATBOX_MESSAGES_ALLOWED_KEY,
			requested.isLocalChatboxMessagesAllowed());
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_MAXIMUM_INTENSITY_PERCENT_KEY,
			requested.getMaximumIntensityPercent());
		configManager.setConfiguration(HapticScapeConfig.GROUP,
			HapticScapeConfig.REMOTE_MAXIMUM_DURATION_MILLIS_KEY,
			requested.getMaximumDurationMillis());
		return requested;
	}
}
