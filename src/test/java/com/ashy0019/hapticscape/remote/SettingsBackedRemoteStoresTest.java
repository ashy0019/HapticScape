package com.ashy0019.hapticscape.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SettingsBackedRemoteStoresTest
{
	@Test
	public void remoteSettingsPersistThroughNeutralWriter()
	{
		Map<String, Object> values = settingsDefaults();
		RemoteSettingsSource source = source(RemoteSettingsSource.class, values);
		List<String> writes = new ArrayList<>();
		SettingsBackedRemoteSettingsStore store = new SettingsBackedRemoteSettingsStore(
			source,
			(key, value) ->
			{
				writes.add(key);
				values.put(key, value);
			}
		);

		Map<String, Object> requestedValues = new HashMap<>(values);
		requestedValues.put("intensityPercent", 77);
		requestedValues.put("clickerEnabled", true);
		requestedValues.put("clickerVolumePercent", 100);
		RemoteSettingsSnapshot requested = RemoteSettingsSnapshot.capture(
			source(RemoteSettingsSource.class, requestedValues)
		);

		RemoteSettingsSnapshot saved = store.save(requested);

		assertEquals(77, saved.getGlobalXpFeedbackSettings().getIntensityPercent());
		assertEquals(false, saved.getClickerSettings().isEnabled());
		assertEquals(70, saved.getClickerSettings().getVolumePercent());
		assertEquals(requested.toConfigurationMap().size(), writes.size());
		assertTrue(writes.contains("intensityPercent"));
		assertTrue(!writes.contains("clickerEnabled"));
		assertTrue(!writes.contains("clickerVolumePercent"));
	}

	@Test
	public void remotePermissionsPersistThroughNeutralWriter()
	{
		Map<String, Object> values = permissionDefaults();
		RemotePermissionsSource source = source(RemotePermissionsSource.class, values);
		List<String> writes = new ArrayList<>();
		SettingsBackedRemotePermissionsStore store =
			new SettingsBackedRemotePermissionsStore(
				source,
				(key, value) ->
				{
					writes.add(key);
					values.put(key, value);
				}
			);
		RemotePermissions requested = new RemotePermissions(
			false,
			true,
			true,
			false,
			true,
			true,
			true,
			42,
			900,
			30_000
		);

		assertEquals(requested, store.save(requested));
		assertEquals(10, writes.size());
		assertTrue(writes.contains("remoteProtectedExitAllowed"));
		assertTrue(writes.contains("remoteMaximumIntensityPercent"));
	}

	@SuppressWarnings("unchecked")
	private static <T> T source(Class<T> type, Map<String, Object> values)
	{
		return (T) Proxy.newProxyInstance(
			type.getClassLoader(),
			new Class<?>[] {type},
			(proxy, method, arguments) ->
			{
				if (!values.containsKey(method.getName()))
				{
					throw new AssertionError("Missing setting: " + method.getName());
				}
				return values.get(method.getName());
			}
		);
	}

	private static Map<String, Object> settingsDefaults()
	{
		Map<String, Object> values = new HashMap<>();
		values.put("minimumXpGain", 1);
		values.put("intensityPercent", 50);
		values.put("pulseDurationMillis", 500);
		values.put("patternPreset", "SINGLE");
		values.put("disabledSkills", "");
		values.put("levelUpFeedbackEnabled", true);
		values.put("levelUpPatternPreset", "DOUBLE");
		values.put("milestoneFeedbackEnabled", true);
		values.put("milestonePatternPreset", "TRIPLE");
		values.put("level99CelebrationEnabled", true);
		values.put("skillFeedbackProfiles", "");
		values.put("notificationFeedbackEnabled", false);
		values.put("notificationIntensityPercent", 50);
		values.put("notificationPatternPreset", "DOUBLE");
		values.put("notificationDurationMillis", 500);
		values.put("notificationRespectFocus", true);
		values.put("alertProfiles", "");
		values.put("alertTriggerSettings", "");
		values.put("customPatterns", "");
		values.put("musicSyncEnabled", false);
		values.put("musicResponse", "RHYTHMIC");
		values.put("musicSensitivityPercent", 100);
		values.put("musicMinimumIntensityPercent", 0);
		values.put("musicMaximumIntensityPercent", 60);
		values.put("clickerEnabled", false);
		values.put("clickerVolumePercent", 70);
		values.put("clickerMinimumXpGain", 1);
		values.put("clickerDisabledSkills", "");
		values.put("clickerXpSequence", "ONE");
		values.put("clickerLevelUpSequence", "ONE");
		values.put("clickerMilestoneSequence", "ONE");
		values.put("clickerGenericNotificationSequence", "NONE");
		values.put("clickerLevelUpEnabled", true);
		values.put("clickerMilestoneEnabled", true);
		values.put("clickerLevel99Enabled", true);
		values.put("clickerGenericNotificationEnabled", false);
		values.put("clickerAlertSettings", "");
		values.put("clickerPhraseRules", "");
		values.put("startWithWindows", false);
		values.put("startMinimized", false);
		return values;
	}

	private static Map<String, Object> permissionDefaults()
	{
		Map<String, Object> values = new HashMap<>();
		values.put("remoteSettingsAllowed", true);
		values.put("remoteHapticsAllowed", true);
		values.put("remoteLiveHapticsAllowed", false);
		values.put("remoteClicksAllowed", true);
		values.put("remoteDesktopNotificationsAllowed", true);
		values.put("remoteLocalChatboxMessagesAllowed", false);
		values.put("remoteProtectedExitAllowed", false);
		values.put("remoteMaximumIntensityPercent", 60);
		values.put("remoteMaximumDurationMillis", 3_000);
		values.put("remoteMaximumLiveDurationMillis", 30_000);
		return values;
	}
}
