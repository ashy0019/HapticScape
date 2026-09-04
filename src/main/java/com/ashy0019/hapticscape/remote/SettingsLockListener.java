package com.ashy0019.hapticscape.remote;

@FunctionalInterface
public interface SettingsLockListener
{
	void onSettingsLockChanged(boolean locked);
}
