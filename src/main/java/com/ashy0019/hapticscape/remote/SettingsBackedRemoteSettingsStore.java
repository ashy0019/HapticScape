package com.ashy0019.hapticscape.remote;

import java.util.Map;
import java.util.Objects;

/**
 * Persists only the explicitly remote-controllable HapticScape settings.
 */
public final class SettingsBackedRemoteSettingsStore implements RemoteSettingsStore
{
	private final RemoteSettingsSource settingsSource;
	private final SettingsWriter settingsWriter;

	public SettingsBackedRemoteSettingsStore(
		RemoteSettingsSource settingsSource,
		SettingsWriter settingsWriter)
	{
		this.settingsSource = Objects.requireNonNull(settingsSource, "settingsSource");
		this.settingsWriter = Objects.requireNonNull(settingsWriter, "settingsWriter");
	}

	@Override
	public RemoteSettingsSnapshot capture()
	{
		return RemoteSettingsSnapshot.capture(settingsSource);
	}

	@Override
	public RemoteSettingsSnapshot save(RemoteSettingsSnapshot settings)
	{
		RemoteSettingsSnapshot requested = Objects.requireNonNull(settings, "settings");
		requested.validate();
		RemoteSettingsSnapshot previous = capture();
		try
		{
			write(requested);
			RemoteSettingsSnapshot canonical = capture();
			canonical.validate();
			return canonical;
		}
		catch (RuntimeException failure)
		{
			try
			{
				write(previous);
			}
			catch (RuntimeException rollbackFailure)
			{
				failure.addSuppressed(rollbackFailure);
			}
			throw failure;
		}
	}

	private void write(RemoteSettingsSnapshot settings)
	{
		for (Map.Entry<String, Object> entry : settings.toConfigurationMap().entrySet())
		{
			settingsWriter.set(entry.getKey(), entry.getValue());
		}
	}
}
