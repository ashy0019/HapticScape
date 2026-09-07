package com.ashy0019.hapticscape.remote;

import java.util.Objects;

/**
 * Chooses the settings currently authoritative for gameplay feedback.
 *
 * <p>Local mode reads the participant's local settings source. Participant Remote
 * Control mode atomically substitutes the most recent validated remote
 * snapshot. Accepted remote settings are also persisted by the session's
 * settings store, so they remain the participant's local settings afterward.</p>
 */
public final class EffectiveSettingsService
{
	private final RemoteSettingsSource localSettings;
	private volatile RemoteSettingsSnapshot remoteSettings;

	public EffectiveSettingsService(RemoteSettingsSource localSettings)
	{
		this.localSettings = Objects.requireNonNull(localSettings, "localSettings");
	}

	public RemoteSettingsSnapshot current()
	{
		RemoteSettingsSnapshot remote = remoteSettings;
		return remote != null ? remote : RemoteSettingsSnapshot.capture(localSettings);
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
