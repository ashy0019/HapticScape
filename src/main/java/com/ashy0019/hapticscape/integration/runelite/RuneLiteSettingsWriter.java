package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.remote.SettingsStore;
import java.util.Objects;
import net.runelite.client.config.ConfigManager;

/** RuneLite-hosted implementation of the neutral HapticScape settings writer. */
public final class RuneLiteSettingsWriter implements SettingsStore
{
	private final ConfigManager configManager;
	private final String group;

	public RuneLiteSettingsWriter(ConfigManager configManager, String group)
	{
		this.configManager = Objects.requireNonNull(configManager, "configManager");
		this.group = Objects.requireNonNull(group, "group");
	}

	@Override
	public String get(String key)
	{
		return configManager.getConfiguration(group, key);
	}

	@Override
	public void set(String key, Object value)
	{
		configManager.setConfiguration(group, key, value);
	}
}
