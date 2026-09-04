package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import java.util.Objects;

/**
 * Chooses the settings currently authoritative for gameplay feedback.
 *
 * <p>Local mode reads the normal RuneLite configuration. Participant Remote
 * Control mode atomically substitutes the most recent validated remote
 * snapshot without modifying the participant's saved local configuration.</p>
 */
public final class EffectiveSettingsService
{
	private final HapticScapeConfig localConfig;
	private volatile RemoteSettingsSnapshot remoteSettings;

	public EffectiveSettingsService(HapticScapeConfig localConfig)
	{
		this.localConfig = Objects.requireNonNull(localConfig, "localConfig");
	}

	public RemoteSettingsSnapshot current()
	{
		RemoteSettingsSnapshot remote = remoteSettings;
		return remote != null ? remote : RemoteSettingsSnapshot.capture(localConfig);
	}

	public boolean isRemoteControlled()
	{
		return remoteSettings != null;
	}

	void applyRemote(RemoteSettingsSnapshot settings)
	{
		RemoteSettingsSnapshot validated = Objects.requireNonNull(settings, "settings");
		validated.validate();
		remoteSettings = validated;
	}

	void clearRemote()
	{
		remoteSettings = null;
	}
}
