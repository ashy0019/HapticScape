package com.ashy0019.hapticscape.update;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UpdatePreferencesTest
{
	@Test
	public void defaultsRequireConsentForAutomaticInstallation()
	{
		UpdatePreferences preferences = UpdatePreferences.defaults();

		assertFalse(preferences.isAutomaticUpdates());
		assertTrue(preferences.isUpdateNotifications());
		assertFalse(preferences.isForceCheck());
	}

	@Test
	public void manualCheckClearsSkipAndRequestsNextLauncherCheck()
	{
		UpdatePreferences preferences = new UpdatePreferences(
			false,
			false,
			"1.6.1",
			"2026-09-02T00:00:00Z",
			false);

		UpdatePreferences requested = preferences.requestUpdateOnNextLaunch();

		assertFalse(requested.isAutomaticUpdates());
		assertFalse(requested.isUpdateNotifications());
		assertTrue(requested.isForceCheck());
		assertNull(requested.getSkippedVersion());
		assertNull(requested.getLastCheckUtc());
	}
}
