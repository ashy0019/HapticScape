package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import java.util.Map;
import java.util.Objects;
import net.runelite.client.config.ConfigManager;

/**
 * Persists only the explicitly remote-controllable HapticScape settings.
 */
public final class ConfigBackedRemoteSettingsStore implements RemoteSettingsStore
{
	private final HapticScapeConfig config;
	private final ConfigManager configManager;

	public ConfigBackedRemoteSettingsStore(
		HapticScapeConfig config,
		ConfigManager configManager)
	{
		this.config = Objects.requireNonNull(config, "config");
		this.configManager = Objects.requireNonNull(configManager, "configManager");
	}

	@Override
	public RemoteSettingsSnapshot capture()
	{
		return RemoteSettingsSnapshot.capture(config);
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
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				entry.getKey(),
				entry.getValue()
			);
		}
	}
}
