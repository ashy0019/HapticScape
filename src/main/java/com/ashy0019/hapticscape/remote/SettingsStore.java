package com.ashy0019.hapticscape.remote;

/** Reads and writes durable HapticScape settings without exposing a host config API. */
public interface SettingsStore extends SettingsWriter
{
	String get(String key);
}
