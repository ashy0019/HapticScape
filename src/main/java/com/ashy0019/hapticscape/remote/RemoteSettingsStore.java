package com.ashy0019.hapticscape.remote;

/**
 * Owns the participant's durable, locally saved feedback settings.
 */
public interface RemoteSettingsStore
{
	RemoteSettingsSnapshot capture();

	/**
	 * Persists a complete validated snapshot and returns the canonical values
	 * read back from the local configuration.
	 */
	RemoteSettingsSnapshot save(RemoteSettingsSnapshot settings);
}
