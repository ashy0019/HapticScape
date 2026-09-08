package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.TestHapticScapeSettings;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class RemoteSettingsSnapshotTest
{
	@Test
	public void sessionDraftChangesOneWhitelistedSettingImmutably()
	{
		RemoteSettingsSnapshot original = RemoteSettingsSnapshot.capture(
			new FixedIntensityConfig(31)
		);
		RemoteSettingsSnapshot updated = original.withConfigurationValue(
			new Gson(),
			HapticScapeSettingKeys.INTENSITY_PERCENT,
			72
		);

		assertEquals(31, original.getGlobalXpFeedbackSettings().getIntensityPercent());
		assertEquals(72, updated.getGlobalXpFeedbackSettings().getIntensityPercent());
	}

	@Test
	public void localConnectionAndSafetySettingsAreNotRemoteControllable()
	{
		RemoteSettingsSnapshot snapshot = RemoteSettingsSnapshot.capture(
			new FixedIntensityConfig(31)
		);
		Map<String, Object> values = snapshot.toConfigurationMap();

		assertFalse(values.containsKey(HapticScapeSettingKeys.REMOTE_RELAY_URL));
		assertFalse(values.containsKey(HapticScapeSettingKeys.REMOTE_SETTINGS_ALLOWED));
		assertFalse(values.containsKey(HapticScapeSettingKeys.REMOTE_HAPTICS_ALLOWED));
		assertFalse(values.containsKey(HapticScapeSettingKeys.REMOTE_CLICKS_ALLOWED));
		assertFalse(values.containsKey(
			HapticScapeSettingKeys.REMOTE_DESKTOP_NOTIFICATIONS_ALLOWED
		));
		assertFalse(values.containsKey(
			HapticScapeSettingKeys.REMOTE_LOCAL_CHATBOX_MESSAGES_ALLOWED
		));
		assertFalse(values.containsKey(
			HapticScapeSettingKeys.REMOTE_MAXIMUM_INTENSITY_PERCENT
		));
		assertFalse(values.containsKey(
			HapticScapeSettingKeys.REMOTE_MAXIMUM_DURATION_MILLIS
		));
		try
		{
			snapshot.withConfigurationValue(
				new Gson(),
				HapticScapeSettingKeys.REMOTE_RELAY_URL,
				"wss://other.example/relay"
			);
			fail("Expected the local relay setting to be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}

	@Test
	public void persistedValuesUseValidatedRanges()
	{
		RemoteSettingsSnapshot updated = RemoteSettingsSnapshot.capture(
			new FixedIntensityConfig(31)
		).withConfigurationValue(
			new Gson(),
			HapticScapeSettingKeys.INTENSITY_PERCENT,
			999
		);

		assertEquals(
			100,
			updated.toConfigurationMap().get(HapticScapeSettingKeys.INTENSITY_PERCENT)
		);
	}

	private static final class FixedIntensityConfig extends TestHapticScapeSettings
	{
		private final int intensity;

		private FixedIntensityConfig(int intensity)
		{
			this.intensity = intensity;
		}

		@Override
		public int intensityPercent()
		{
			return intensity;
		}
	}
}
