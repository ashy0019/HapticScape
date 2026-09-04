package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
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
			HapticScapeConfig.INTENSITY_PERCENT_KEY,
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

		assertFalse(values.containsKey(HapticScapeConfig.REMOTE_RELAY_URL_KEY));
		try
		{
			snapshot.withConfigurationValue(
				new Gson(),
				HapticScapeConfig.REMOTE_RELAY_URL_KEY,
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
			HapticScapeConfig.INTENSITY_PERCENT_KEY,
			999
		);

		assertEquals(
			100,
			updated.toConfigurationMap().get(HapticScapeConfig.INTENSITY_PERCENT_KEY)
		);
	}

	private static final class FixedIntensityConfig implements HapticScapeConfig
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
