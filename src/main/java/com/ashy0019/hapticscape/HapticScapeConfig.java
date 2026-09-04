package com.ashy0019.hapticscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(HapticScapeConfig.GROUP)
public interface HapticScapeConfig extends Config
{
	String GROUP = "hapticscape";
	String MINIMUM_XP_GAIN_KEY = "minimumXpGain";
	String INTENSITY_PERCENT_KEY = "intensityPercent";
	String PULSE_DURATION_MILLIS_KEY = "pulseDurationMillis";
	String PATTERN_PRESET_KEY = "patternPreset";
	String DISABLED_SKILLS_KEY = "disabledSkills";
	String LEVEL_UP_FEEDBACK_ENABLED_KEY = "levelUpFeedbackEnabled";
	String LEVEL_UP_PATTERN_PRESET_KEY = "levelUpPatternPreset";
	String MILESTONE_FEEDBACK_ENABLED_KEY = "milestoneFeedbackEnabled";
	String MILESTONE_PATTERN_PRESET_KEY = "milestonePatternPreset";
	String LEVEL_99_CELEBRATION_ENABLED_KEY = "level99CelebrationEnabled";
	String SKILL_FEEDBACK_PROFILES_KEY = "skillFeedbackProfiles";
	String NOTIFICATION_FEEDBACK_ENABLED_KEY = "notificationFeedbackEnabled";
	String NOTIFICATION_INTENSITY_PERCENT_KEY = "notificationIntensityPercent";
	String NOTIFICATION_PATTERN_PRESET_KEY = "notificationPatternPreset";
	String NOTIFICATION_DURATION_MILLIS_KEY = "notificationDurationMillis";
	String NOTIFICATION_RESPECT_FOCUS_KEY = "notificationRespectFocus";
	String ALERT_PROFILES_KEY = "alertProfiles";
	String ALERT_TRIGGER_SETTINGS_KEY = "alertTriggerSettings";
	String CUSTOM_PATTERNS_KEY = "customPatterns";
	String MUSIC_SYNC_ENABLED_KEY = "musicSyncEnabled";
	String MUSIC_RESPONSE_KEY = "musicResponse";
	String MUSIC_SENSITIVITY_PERCENT_KEY = "musicSensitivityPercent";
	String MUSIC_MINIMUM_INTENSITY_PERCENT_KEY = "musicMinimumIntensityPercent";
	String MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY = "musicMaximumIntensityPercent";
	String CLICKER_ENABLED_KEY = "clickerEnabled";
	String CLICKER_VOLUME_PERCENT_KEY = "clickerVolumePercent";
	String CLICKER_MINIMUM_XP_GAIN_KEY = "clickerMinimumXpGain";
	String CLICKER_DISABLED_SKILLS_KEY = "clickerDisabledSkills";
	String CLICKER_LEVEL_UP_ENABLED_KEY = "clickerLevelUpEnabled";
	String CLICKER_MILESTONE_ENABLED_KEY = "clickerMilestoneEnabled";
	String CLICKER_LEVEL_99_ENABLED_KEY = "clickerLevel99Enabled";
	String CLICKER_GENERIC_NOTIFICATION_ENABLED_KEY = "clickerGenericNotificationEnabled";
	String CLICKER_ALERT_SETTINGS_KEY = "clickerAlertSettings";
	String CLICKER_PHRASE_RULES_KEY = "clickerPhraseRules";
	String REMOTE_RELAY_URL_KEY = "remoteRelayUrl";
	String DEFAULT_REMOTE_RELAY_URL =
		"wss://hapticscape-remote-relay.hapticscape.workers.dev/relay";
	String REMOTE_SETTINGS_ALLOWED_KEY = "remoteSettingsAllowed";
	String REMOTE_HAPTICS_ALLOWED_KEY = "remoteHapticsAllowed";
	String REMOTE_LIVE_HAPTICS_ALLOWED_KEY = "remoteLiveHapticsAllowed";
	String REMOTE_CLICKS_ALLOWED_KEY = "remoteClicksAllowed";
	String REMOTE_DESKTOP_NOTIFICATIONS_ALLOWED_KEY = "remoteDesktopNotificationsAllowed";
	String REMOTE_LOCAL_CHATBOX_MESSAGES_ALLOWED_KEY = "remoteLocalChatboxMessagesAllowed";
	String REMOTE_MAXIMUM_INTENSITY_PERCENT_KEY = "remoteMaximumIntensityPercent";
	String REMOTE_MAXIMUM_DURATION_MILLIS_KEY = "remoteMaximumDurationMillis";
	String REMOTE_MAXIMUM_LIVE_DURATION_MILLIS_KEY = "remoteMaximumLiveDurationMillis";

	@ConfigItem(
		keyName = "intifaceServer",
		name = "Intiface server",
		description = "WebSocket URI for the Intiface server",
		position = 0,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default String intifaceServer()
	{
		return "ws://localhost:12345";
	}

	@Range(min = 1, max = 200_000_000)
	@ConfigItem(
		keyName = MINIMUM_XP_GAIN_KEY,
		name = "Minimum XP gain",
		description = "Minimum XP gained by one stat change before feedback is triggered",
		position = 1,
		hidden = true
	)
	default int minimumXpGain()
	{
		return 1;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = INTENSITY_PERCENT_KEY,
		name = "Intensity",
		description = "Device intensity as a percentage",
		position = 2,
		hidden = true
	)
	default int intensityPercent()
	{
		return 50;
	}

	@Range(min = 50, max = 10_000)
	@ConfigItem(
		keyName = PULSE_DURATION_MILLIS_KEY,
		name = "Pulse duration",
		description = "Feedback duration in milliseconds",
		position = 3,
		hidden = true
	)
	default int pulseDurationMillis()
	{
		return 500;
	}

	@ConfigItem(
		keyName = PATTERN_PRESET_KEY,
		name = "Haptic pattern",
		description = "Pattern used for XP feedback",
		position = 4,
		hidden = true
	)
	default String patternPreset()
	{
		return HapticPatternSelection.SINGLE.toConfigValue();
	}

	@ConfigItem(
		keyName = DISABLED_SKILLS_KEY,
		name = "Disabled XP skills",
		description = "Skill identifiers which do not trigger XP feedback",
		position = 5,
		hidden = true
	)
	default String disabledSkills()
	{
		return "";
	}

	@ConfigItem(
		keyName = LEVEL_UP_FEEDBACK_ENABLED_KEY,
		name = "Level-up feedback",
		description = "Use distinct haptic feedback when a skill level increases",
		position = 6,
		hidden = true
	)
	default boolean levelUpFeedbackEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = LEVEL_UP_PATTERN_PRESET_KEY,
		name = "Level-up pattern",
		description = "Pattern used for ordinary skill level increases",
		position = 7,
		hidden = true
	)
	default String levelUpPatternPreset()
	{
		return HapticPatternSelection.DOUBLE.toConfigValue();
	}

	@ConfigItem(
		keyName = MILESTONE_FEEDBACK_ENABLED_KEY,
		name = "Milestone feedback",
		description = "Use special patterns for levels 10 through 90",
		position = 8,
		hidden = true
	)
	default boolean milestoneFeedbackEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = MILESTONE_PATTERN_PRESET_KEY,
		name = "Milestone pattern",
		description = "Pattern used for skill levels 10 through 90",
		position = 9,
		hidden = true
	)
	default String milestonePatternPreset()
	{
		return HapticPatternSelection.TRIPLE.toConfigValue();
	}

	@ConfigItem(
		keyName = LEVEL_99_CELEBRATION_ENABLED_KEY,
		name = "Level 99 celebration",
		description = "Celebrate real skill level 99 with the mastery ceremony",
		position = 10,
		hidden = true
	)
	default boolean level99CelebrationEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = SKILL_FEEDBACK_PROFILES_KEY,
		name = "Skill XP profiles",
		description = "Per-skill XP feedback overrides",
		position = 11,
		hidden = true
	)
	default String skillFeedbackProfiles()
	{
		return "";
	}

	@ConfigItem(
		keyName = NOTIFICATION_FEEDBACK_ENABLED_KEY,
		name = "RuneLite notifications",
		description = "Trigger haptic feedback for RuneLite notifications",
		position = 12,
		hidden = true
	)
	default boolean notificationFeedbackEnabled()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = NOTIFICATION_INTENSITY_PERCENT_KEY,
		name = "Notification intensity",
		description = "Device intensity used for RuneLite notification feedback",
		position = 13,
		hidden = true
	)
	default int notificationIntensityPercent()
	{
		return 50;
	}

	@ConfigItem(
		keyName = NOTIFICATION_PATTERN_PRESET_KEY,
		name = "Notification pattern",
		description = "Pattern used for RuneLite notification feedback",
		position = 14,
		hidden = true
	)
	default String notificationPatternPreset()
	{
		return HapticPatternSelection.DOUBLE.toConfigValue();
	}

	@Range(min = 50, max = 10_000)
	@ConfigItem(
		keyName = NOTIFICATION_DURATION_MILLIS_KEY,
		name = "Notification duration",
		description = "Total duration of a RuneLite notification pattern in milliseconds",
		position = 15,
		hidden = true
	)
	default int notificationDurationMillis()
	{
		return 500;
	}

	@ConfigItem(
		keyName = NOTIFICATION_RESPECT_FOCUS_KEY,
		name = "Respect notification focus",
		description = "Suppress haptics while focused when RuneLite suppresses the notification",
		position = 16,
		hidden = true
	)
	default boolean notificationRespectFocus()
	{
		return true;
	}

	@ConfigItem(
		keyName = ALERT_PROFILES_KEY,
		name = "Semantic alert profiles",
		description = "Per-category behavior and haptic settings for gameplay alerts",
		position = 17,
		hidden = true
	)
	default String alertProfiles()
	{
		return "";
	}

	@ConfigItem(
		keyName = ALERT_TRIGGER_SETTINGS_KEY,
		name = "Semantic alert triggers",
		description = "Category-specific thresholds and trigger values for gameplay alerts",
		position = 18,
		hidden = true
	)
	default String alertTriggerSettings()
	{
		return "";
	}

	@ConfigItem(
		keyName = CUSTOM_PATTERNS_KEY,
		name = "Pattern Forge library",
		description = "Named custom haptic patterns and timing created in the Pattern Forge",
		position = 19,
		hidden = true
	)
	default String customPatterns()
	{
		return "";
	}

	@ConfigItem(
		keyName = MUSIC_SYNC_ENABLED_KEY,
		name = "Music sync",
		description = "Drive low-priority haptic feedback from Windows system audio",
		position = 20,
		hidden = true
	)
	default boolean musicSyncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = MUSIC_RESPONSE_KEY,
		name = "Music response",
		description = "How music energy is translated into haptic intensity",
		position = 21,
		hidden = true
	)
	default String musicResponse()
	{
		return "RHYTHMIC";
	}

	@Range(min = 25, max = 200)
	@ConfigItem(
		keyName = MUSIC_SENSITIVITY_PERCENT_KEY,
		name = "Music sensitivity",
		description = "Sensitivity of the system audio analyzer",
		position = 22,
		hidden = true
	)
	default int musicSensitivityPercent()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = MUSIC_MINIMUM_INTENSITY_PERCENT_KEY,
		name = "Music minimum intensity",
		description = "Lowest non-silent music-sync intensity",
		position = 23,
		hidden = true
	)
	default int musicMinimumIntensityPercent()
	{
		return 0;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY,
		name = "Music maximum intensity",
		description = "Intensity ceiling for music sync",
		position = 24,
		hidden = true
	)
	default int musicMaximumIntensityPercent()
	{
		return 60;
	}

	@ConfigItem(
		keyName = CLICKER_ENABLED_KEY,
		name = "Clicker",
		description = "Play independent auditory clicker feedback",
		position = 25,
		hidden = true
	)
	default boolean clickerEnabled()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = CLICKER_VOLUME_PERCENT_KEY,
		name = "Clicker volume",
		description = "Auditory clicker volume as a percentage",
		position = 26,
		hidden = true
	)
	default int clickerVolumePercent()
	{
		return 70;
	}

	@Range(min = 1, max = 200_000_000)
	@ConfigItem(
		keyName = CLICKER_MINIMUM_XP_GAIN_KEY,
		name = "Clicker minimum XP gain",
		description = "Minimum XP gained by one stat change before a click is triggered",
		position = 27,
		hidden = true
	)
	default int clickerMinimumXpGain()
	{
		return 1;
	}

	@ConfigItem(
		keyName = CLICKER_DISABLED_SKILLS_KEY,
		name = "Clicker disabled skills",
		description = "Skill identifiers which do not trigger clicker feedback",
		position = 28,
		hidden = true
	)
	default String clickerDisabledSkills()
	{
		return SkillSelection.allEnabled().withAllEnabled(false).toConfigValue();
	}

	@ConfigItem(
		keyName = CLICKER_LEVEL_UP_ENABLED_KEY,
		name = "Clicker level-ups",
		description = "Click once when a real skill level increases",
		position = 29,
		hidden = true
	)
	default boolean clickerLevelUpEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = CLICKER_MILESTONE_ENABLED_KEY,
		name = "Clicker milestones",
		description = "Click once for skill levels 10 through 90",
		position = 30,
		hidden = true
	)
	default boolean clickerMilestoneEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = CLICKER_LEVEL_99_ENABLED_KEY,
		name = "Clicker level 99",
		description = "Click once when a skill reaches level 99",
		position = 31,
		hidden = true
	)
	default boolean clickerLevel99Enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = CLICKER_GENERIC_NOTIFICATION_ENABLED_KEY,
		name = "Clicker generic notifications",
		description = "Click for unclassified RuneLite notifications",
		position = 32,
		hidden = true
	)
	default boolean clickerGenericNotificationEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = CLICKER_ALERT_SETTINGS_KEY,
		name = "Clicker semantic alerts",
		description = "Semantic alert categories which produce auditory clicks",
		position = 33,
		hidden = true
	)
	default String clickerAlertSettings()
	{
		return "";
	}

	@ConfigItem(
		keyName = CLICKER_PHRASE_RULES_KEY,
		name = "Clicker phrase rules",
		description = "Locally evaluated chat-message rules which produce auditory clicks",
		position = 34,
		hidden = true
	)
	default String clickerPhraseRules()
	{
		return "";
	}

	@ConfigItem(
		keyName = REMOTE_RELAY_URL_KEY,
		name = "Remote relay URL",
		description = "WebSocket relay used for opt-in Remote Control sessions",
		position = 35,
		hidden = true,
		warning = "Remote Control connects to a 3rd-party relay. The relay operator can see your IP address; HapticScape does not directly send it to the paired client."
	)
	default String remoteRelayUrl()
	{
		return DEFAULT_REMOTE_RELAY_URL;
	}

	static String resolveRemoteRelayUrl(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return DEFAULT_REMOTE_RELAY_URL;
		}
		return configuredValue.trim();
	}

	@ConfigItem(
		keyName = REMOTE_SETTINGS_ALLOWED_KEY,
		name = "Allow remote settings",
		description = "Allow a connected controller to change HapticScape feedback settings",
		position = 36,
		hidden = true
	)
	default boolean remoteSettingsAllowed()
	{
		return true;
	}

	@ConfigItem(
		keyName = REMOTE_HAPTICS_ALLOWED_KEY,
		name = "Allow remote haptics",
		description = "Allow a connected controller to request bounded haptic actions",
		position = 37,
		hidden = true
	)
	default boolean remoteHapticsAllowed()
	{
		return true;
	}

	@ConfigItem(
		keyName = REMOTE_LIVE_HAPTICS_ALLOWED_KEY,
		name = "Allow live remote haptics",
		description = "Allow a connected controller to continuously control haptic intensity while holding the Live Forge",
		position = 43,
		hidden = true
	)
	default boolean remoteLiveHapticsAllowed()
	{
		return false;
	}

	@ConfigItem(
		keyName = REMOTE_CLICKS_ALLOWED_KEY,
		name = "Allow remote clicks",
		description = "Allow a connected controller to play the local click sound",
		position = 38,
		hidden = true
	)
	default boolean remoteClicksAllowed()
	{
		return true;
	}

	@ConfigItem(
		keyName = REMOTE_DESKTOP_NOTIFICATIONS_ALLOWED_KEY,
		name = "Allow remote notifications",
		description = "Allow a connected controller to show a local RuneLite desktop notification",
		position = 39,
		hidden = true
	)
	default boolean remoteDesktopNotificationsAllowed()
	{
		return true;
	}

	@ConfigItem(
		keyName = REMOTE_LOCAL_CHATBOX_MESSAGES_ALLOWED_KEY,
		name = "Allow local chatbox notices",
		description = "Allow remote text to appear as a local-only HapticScape console line; nothing is sent to Jagex or other players",
		position = 40,
		hidden = true
	)
	default boolean remoteLocalChatboxMessagesAllowed()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = REMOTE_MAXIMUM_INTENSITY_PERCENT_KEY,
		name = "Maximum remote intensity",
		description = "Participant-owned intensity ceiling for direct remote haptic actions",
		position = 41,
		hidden = true
	)
	default int remoteMaximumIntensityPercent()
	{
		return 60;
	}

	@Range(min = 50, max = 10_000)
	@ConfigItem(
		keyName = REMOTE_MAXIMUM_DURATION_MILLIS_KEY,
		name = "Maximum remote duration",
		description = "Participant-owned duration ceiling for direct remote haptic actions",
		position = 42,
		hidden = true
	)
	default int remoteMaximumDurationMillis()
	{
		return 3_000;
	}

	@Range(min = 1_000, max = 300_000)
	@ConfigItem(
		keyName = REMOTE_MAXIMUM_LIVE_DURATION_MILLIS_KEY,
		name = "Maximum live hold",
		description = "Participant-owned maximum duration of one continuous Live Forge gesture",
		position = 44,
		hidden = true
	)
	default int remoteMaximumLiveDurationMillis()
	{
		return 30_000;
	}
}
