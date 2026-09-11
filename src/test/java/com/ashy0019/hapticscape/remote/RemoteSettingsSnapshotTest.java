package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.TestHapticScapeSettings;
import com.ashy0019.hapticscape.clicker.ClickSequence;

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
	public void localClickOutputAuthorityIsNotRemoteControllable()
	{
		RemoteSettingsSnapshot snapshot = RemoteSettingsSnapshot.capture(
			new FixedIntensityConfig(31)
		);
		Map<String, Object> values = snapshot.toConfigurationMap();

		assertFalse(values.containsKey(HapticScapeSettingKeys.CLICKER_ENABLED));
		assertFalse(values.containsKey(HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT));
		try
		{
			snapshot.withConfigurationValue(
				new Gson(),
				HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT,
				100
			);
			fail("Expected local click volume to be rejected as a remote setting");
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


	@Test
	public void clickSequencesAreRemoteSettingsButLevelNinetyNineClickIsNot()
	{
		RemoteSettingsSnapshot snapshot = RemoteSettingsSnapshot.capture(
			new FixedIntensityConfig(31)
		);
		Map<String, Object> values = snapshot.toConfigurationMap();

		assertEquals("ONE", values.get(HapticScapeSettingKeys.CLICKER_XP_SEQUENCE));
		assertEquals("ONE", values.get(HapticScapeSettingKeys.CLICKER_LEVEL_UP_SEQUENCE));
		assertEquals("ONE", values.get(HapticScapeSettingKeys.CLICKER_MILESTONE_SEQUENCE));
		assertFalse(values.containsKey(HapticScapeSettingKeys.CLICKER_LEVEL_99_ENABLED));
	}

	@Test
	public void sequenceDraftValidatesAndRoundTrips()
	{
		RemoteSettingsSnapshot updated = RemoteSettingsSnapshot.capture(
			new FixedIntensityConfig(31)
		).withConfigurationValue(
			new Gson(),
			HapticScapeSettingKeys.CLICKER_LEVEL_UP_SEQUENCE,
			"THREE"
		);

		assertEquals(ClickSequence.THREE, updated.getClickerXpSettings().getLevelUpOverride());
	}

	@Test
	public void skillClickProfileOverridesGlobalClickPolicy()
	{
		RemoteSettingsSnapshot snapshot = RemoteSettingsSnapshot.capture(
			new SkillClickProfileConfig()
		);

		assertEquals(25, snapshot.getClickerXpSettings("ranged").getMinimumXpGain());
		assertEquals(ClickSequence.TWO, snapshot.getClickerXpSettings("ranged").getXpGainSequence());
		assertEquals(1, snapshot.getClickerXpSettings("cooking").getMinimumXpGain());
	}

	private static final class SkillClickProfileConfig extends TestHapticScapeSettings
	{
		@Override
		public String skillClickProfiles()
		{
			return "v1|RANGED,25,TWO,THREE,ONE";
		}
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
