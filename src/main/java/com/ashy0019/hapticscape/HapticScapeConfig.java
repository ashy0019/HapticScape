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
	String SKILL_FEEDBACK_PROFILES_KEY = "skillFeedbackProfiles";
	String NOTIFICATION_FEEDBACK_ENABLED_KEY = "notificationFeedbackEnabled";
	String NOTIFICATION_INTENSITY_PERCENT_KEY = "notificationIntensityPercent";
	String NOTIFICATION_PATTERN_PRESET_KEY = "notificationPatternPreset";
	String NOTIFICATION_DURATION_MILLIS_KEY = "notificationDurationMillis";
	String NOTIFICATION_RESPECT_FOCUS_KEY = "notificationRespectFocus";
	String CUSTOM_PATTERNS_KEY = "customPatterns";

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
		description = "Use special patterns for levels 10 through 90 and level 99",
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
		keyName = SKILL_FEEDBACK_PROFILES_KEY,
		name = "Skill XP profiles",
		description = "Per-skill XP feedback overrides",
		position = 10,
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
		position = 11,
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
		position = 12,
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
		position = 13,
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
		position = 14,
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
		position = 15,
		hidden = true
	)
	default boolean notificationRespectFocus()
	{
		return true;
	}

	@ConfigItem(
		keyName = CUSTOM_PATTERNS_KEY,
		name = "Pattern Forge library",
		description = "Named custom haptic patterns and timing created in the Pattern Forge",
		position = 16,
		hidden = true
	)
	default String customPatterns()
	{
		return "";
	}
}
