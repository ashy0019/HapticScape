package com.ashy0019.hapticscape.ui;

/**
 * Receives one editable HapticScape setting from a panel control.
 *
 * <p>The main panel routes the change either to RuneLite's local configuration
 * or to the controller's remote-session draft. Child panels therefore do not
 * need to know which settings document they are editing.</p>
 */
@FunctionalInterface
interface SettingsChangeSink
{
	void set(String key, Object value);
}
