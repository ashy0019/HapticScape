package com.ashy0019.hapticscape.remote;

/** Writes one durable HapticScape setting without exposing a host config API. */
@FunctionalInterface
public interface SettingsWriter
{
	void set(String key, Object value);
}
