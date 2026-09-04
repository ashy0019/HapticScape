package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.AlertProfiles;
import com.ashy0019.hapticscape.AlertTriggerSettings;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.NotificationFeedbackSettings;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.clicker.ClickerAlertSettings;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.music.MusicResponse;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Skill;

/**
 * Versioned, remote-controllable HapticScape settings.
 *
 * <p>Connection and safety settings are intentionally absent. In particular,
 * the Intiface server URI, connect/disconnect state, Remote Control pairing,
 * Emergency Off, and session termination remain local-only.</p>
 */
public final class RemoteSettingsSnapshot
{
	public static final int SCHEMA_VERSION = 1;

	private final int schemaVersion;
	private final int minimumXpGain;
	private final int intensityPercent;
	private final int pulseDurationMillis;
	private final String patternPreset;
	private final String disabledSkills;
	private final boolean levelUpFeedbackEnabled;
	private final String levelUpPatternPreset;
	private final boolean milestoneFeedbackEnabled;
	private final String milestonePatternPreset;
	private final boolean level99CelebrationEnabled;
	private final String skillFeedbackProfiles;
	private final boolean notificationFeedbackEnabled;
	private final int notificationIntensityPercent;
	private final String notificationPatternPreset;
	private final int notificationDurationMillis;
	private final boolean notificationRespectFocus;
	private final String alertProfiles;
	private final String alertTriggerSettings;
	private final String customPatterns;
	private final boolean musicSyncEnabled;
	private final String musicResponse;
	private final int musicSensitivityPercent;
	private final int musicMinimumIntensityPercent;
	private final int musicMaximumIntensityPercent;
	private final boolean clickerEnabled;
	private final int clickerVolumePercent;
	private final int clickerMinimumXpGain;
	private final String clickerDisabledSkills;
	private final boolean clickerLevelUpEnabled;
	private final boolean clickerMilestoneEnabled;
	private final boolean clickerLevel99Enabled;
	private final boolean clickerGenericNotificationEnabled;
	private final String clickerAlertSettings;
	private final String clickerPhraseRules;

	private RemoteSettingsSnapshot(HapticScapeConfig config)
	{
		schemaVersion = SCHEMA_VERSION;
		minimumXpGain = config.minimumXpGain();
		intensityPercent = config.intensityPercent();
		pulseDurationMillis = config.pulseDurationMillis();
		patternPreset = config.patternPreset();
		disabledSkills = config.disabledSkills();
		levelUpFeedbackEnabled = config.levelUpFeedbackEnabled();
		levelUpPatternPreset = config.levelUpPatternPreset();
		milestoneFeedbackEnabled = config.milestoneFeedbackEnabled();
		milestonePatternPreset = config.milestonePatternPreset();
		level99CelebrationEnabled = config.level99CelebrationEnabled();
		skillFeedbackProfiles = config.skillFeedbackProfiles();
		notificationFeedbackEnabled = config.notificationFeedbackEnabled();
		notificationIntensityPercent = config.notificationIntensityPercent();
		notificationPatternPreset = config.notificationPatternPreset();
		notificationDurationMillis = config.notificationDurationMillis();
		notificationRespectFocus = config.notificationRespectFocus();
		alertProfiles = config.alertProfiles();
		alertTriggerSettings = config.alertTriggerSettings();
		customPatterns = config.customPatterns();
		musicSyncEnabled = config.musicSyncEnabled();
		musicResponse = config.musicResponse();
		musicSensitivityPercent = config.musicSensitivityPercent();
		musicMinimumIntensityPercent = config.musicMinimumIntensityPercent();
		musicMaximumIntensityPercent = config.musicMaximumIntensityPercent();
		clickerEnabled = config.clickerEnabled();
		clickerVolumePercent = config.clickerVolumePercent();
		clickerMinimumXpGain = config.clickerMinimumXpGain();
		clickerDisabledSkills = config.clickerDisabledSkills();
		clickerLevelUpEnabled = config.clickerLevelUpEnabled();
		clickerMilestoneEnabled = config.clickerMilestoneEnabled();
		clickerLevel99Enabled = config.clickerLevel99Enabled();
		clickerGenericNotificationEnabled = config.clickerGenericNotificationEnabled();
		clickerAlertSettings = config.clickerAlertSettings();
		clickerPhraseRules = config.clickerPhraseRules();
	}

	public static RemoteSettingsSnapshot capture(HapticScapeConfig config)
	{
		return new RemoteSettingsSnapshot(Objects.requireNonNull(config, "config"));
	}

	RemoteSettingsSnapshot withConfigurationValue(Gson gson, String key, Object value)
	{
		Objects.requireNonNull(gson, "gson");
		if (!toConfigurationMap().containsKey(key))
		{
			throw new IllegalArgumentException("Setting is not remotely controllable: " + key);
		}
		JsonObject json = gson.toJsonTree(this).getAsJsonObject();
		json.add(key, gson.toJsonTree(value));
		RemoteSettingsSnapshot updated = gson.fromJson(json, RemoteSettingsSnapshot.class);
		updated.validate();
		return updated;
	}

	Map<String, Object> toConfigurationMap()
	{
		CustomPatternLibrary patterns = getCustomPatterns();
		XpFeedbackSettings globalXp = getGlobalXpFeedbackSettings();
		NotificationFeedbackSettings notifications = getNotificationFeedbackSettings();
		MusicSyncSettings music = getMusicSyncSettings();
		ClickerSettings clicker = getClickerSettings();
		ClickerXpSettings clickerXp = getClickerXpSettings();
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(HapticScapeConfig.MINIMUM_XP_GAIN_KEY, globalXp.getMinimumXpGain());
		values.put(HapticScapeConfig.INTENSITY_PERCENT_KEY, globalXp.getIntensityPercent());
		values.put(HapticScapeConfig.PULSE_DURATION_MILLIS_KEY, globalXp.getDurationMillis());
		values.put(
			HapticScapeConfig.PATTERN_PRESET_KEY,
			globalXp.getPatternSelection().toConfigValue()
		);
		values.put(
			HapticScapeConfig.DISABLED_SKILLS_KEY,
			getHapticSkillSelection().toConfigValue()
		);
		values.put(HapticScapeConfig.LEVEL_UP_FEEDBACK_ENABLED_KEY, levelUpFeedbackEnabled);
		values.put(
			HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY,
			getLevelUpPatternPreset().toConfigValue()
		);
		values.put(HapticScapeConfig.MILESTONE_FEEDBACK_ENABLED_KEY, milestoneFeedbackEnabled);
		values.put(
			HapticScapeConfig.MILESTONE_PATTERN_PRESET_KEY,
			getMilestonePatternPreset().toConfigValue()
		);
		values.put(HapticScapeConfig.LEVEL_99_CELEBRATION_ENABLED_KEY, level99CelebrationEnabled);
		values.put(
			HapticScapeConfig.SKILL_FEEDBACK_PROFILES_KEY,
			getSkillFeedbackProfiles().toConfigValue()
		);
		values.put(HapticScapeConfig.NOTIFICATION_FEEDBACK_ENABLED_KEY, notifications.isEnabled());
		values.put(
			HapticScapeConfig.NOTIFICATION_INTENSITY_PERCENT_KEY,
			notifications.getIntensityPercent()
		);
		values.put(
			HapticScapeConfig.NOTIFICATION_PATTERN_PRESET_KEY,
			notifications.getPatternSelection().toConfigValue()
		);
		values.put(
			HapticScapeConfig.NOTIFICATION_DURATION_MILLIS_KEY,
			notifications.getDurationMillis()
		);
		values.put(
			HapticScapeConfig.NOTIFICATION_RESPECT_FOCUS_KEY,
			notifications.isRespectRuneLiteFocus()
		);
		values.put(HapticScapeConfig.ALERT_PROFILES_KEY, getAlertProfiles().toConfigValue());
		values.put(
			HapticScapeConfig.ALERT_TRIGGER_SETTINGS_KEY,
			getAlertTriggerSettings().toConfigValue()
		);
		values.put(HapticScapeConfig.CUSTOM_PATTERNS_KEY, patterns.toConfigValue());
		values.put(HapticScapeConfig.MUSIC_SYNC_ENABLED_KEY, music.isEnabled());
		values.put(HapticScapeConfig.MUSIC_RESPONSE_KEY, music.getResponse().name());
		values.put(
			HapticScapeConfig.MUSIC_SENSITIVITY_PERCENT_KEY,
			music.getSensitivityPercent()
		);
		values.put(
			HapticScapeConfig.MUSIC_MINIMUM_INTENSITY_PERCENT_KEY,
			music.getMinimumIntensityPercent()
		);
		values.put(
			HapticScapeConfig.MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY,
			music.getMaximumIntensityPercent()
		);
		values.put(HapticScapeConfig.CLICKER_ENABLED_KEY, clicker.isEnabled());
		values.put(HapticScapeConfig.CLICKER_VOLUME_PERCENT_KEY, clicker.getVolumePercent());
		values.put(
			HapticScapeConfig.CLICKER_MINIMUM_XP_GAIN_KEY,
			clickerXp.getMinimumXpGain()
		);
		values.put(
			HapticScapeConfig.CLICKER_DISABLED_SKILLS_KEY,
			getClickSkillSelection().toConfigValue()
		);
		values.put(HapticScapeConfig.CLICKER_LEVEL_UP_ENABLED_KEY, clickerXp.isLevelUpEnabled());
		values.put(
			HapticScapeConfig.CLICKER_MILESTONE_ENABLED_KEY,
			clickerXp.isMilestoneEnabled()
		);
		values.put(HapticScapeConfig.CLICKER_LEVEL_99_ENABLED_KEY, clickerXp.isLevel99Enabled());
		values.put(HapticScapeConfig.CLICKER_GENERIC_NOTIFICATION_ENABLED_KEY, clickerGenericNotificationEnabled);
		values.put(
			HapticScapeConfig.CLICKER_ALERT_SETTINGS_KEY,
			getClickerAlertSettings().toConfigValue()
		);
		values.put(
			HapticScapeConfig.CLICKER_PHRASE_RULES_KEY,
			getClickerPhraseRules().toConfigValue()
		);
		return Collections.unmodifiableMap(values);
	}

	public void validate()
	{
		if (schemaVersion != SCHEMA_VERSION)
		{
			throw new IllegalArgumentException(
				"Unsupported remote settings schema " + schemaVersion
			);
		}
		// Construct every derived value once so malformed remote data is rejected
		// before it becomes authoritative.
		getGlobalXpFeedbackSettings();
		getCustomPatterns();
		getSkillFeedbackProfiles();
		getNotificationFeedbackSettings();
		getAlertProfiles();
		getAlertTriggerSettings();
		getMusicSyncSettings();
		getClickerSettings();
		getClickerXpSettings();
		getClickerPhraseRules();
		ClickerAlertSettings.fromConfigValue(clickerAlertSettings);
	}

	public XpFeedbackSettings getGlobalXpFeedbackSettings()
	{
		return new XpFeedbackSettings(
			clamp(minimumXpGain, XpFeedbackSettings.MINIMUM_XP_GAIN, XpFeedbackSettings.MAXIMUM_XP_GAIN),
			clamp(intensityPercent, 0, 100),
			clamp(
				pulseDurationMillis,
				XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
				XpFeedbackSettings.MAXIMUM_DURATION_MILLIS
			),
			HapticPatternSelection.fromConfigValue(patternPreset)
				.resolveAgainst(getCustomPatterns())
		);
	}

	public XpFeedbackSettings getXpFeedbackSettings(Skill skill)
	{
		return getSkillFeedbackProfiles().resolve(skill, getGlobalXpFeedbackSettings());
	}

	public SkillFeedbackProfiles getSkillFeedbackProfiles()
	{
		return SkillFeedbackProfiles.fromConfigValue(skillFeedbackProfiles)
			.replaceMissingCustomPatterns(getCustomPatterns());
	}

	public SkillSelection getHapticSkillSelection()
	{
		return SkillSelection.fromConfigValue(disabledSkills);
	}

	public SkillSelection getClickSkillSelection()
	{
		return SkillSelection.fromConfigValue(clickerDisabledSkills);
	}

	public boolean isHapticSkillEnabled(Skill skill)
	{
		return getHapticSkillSelection().isEnabled(skill);
	}

	public boolean isClickSkillEnabled(Skill skill)
	{
		return getClickSkillSelection().isEnabled(skill);
	}

	public boolean isLevelUpFeedbackEnabled()
	{
		return levelUpFeedbackEnabled;
	}

	public boolean isMilestoneFeedbackEnabled()
	{
		return milestoneFeedbackEnabled;
	}

	public boolean isLevel99CelebrationEnabled()
	{
		return level99CelebrationEnabled;
	}

	public HapticPatternSelection getLevelUpPatternPreset()
	{
		return HapticPatternSelection.fromConfigValue(levelUpPatternPreset)
			.resolveAgainst(getCustomPatterns());
	}

	public HapticPatternSelection getMilestonePatternPreset()
	{
		return HapticPatternSelection.fromConfigValue(milestonePatternPreset)
			.resolveAgainst(getCustomPatterns());
	}

	public CustomPatternLibrary getCustomPatterns()
	{
		return CustomPatternLibrary.fromConfigValue(customPatterns);
	}

	public NotificationFeedbackSettings getNotificationFeedbackSettings()
	{
		return new NotificationFeedbackSettings(
			notificationFeedbackEnabled,
			notificationRespectFocus,
			clamp(notificationIntensityPercent, 0, 100),
			clamp(
				notificationDurationMillis,
				NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
				NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS
			),
			HapticPatternSelection.fromConfigValue(notificationPatternPreset)
				.resolveAgainst(getCustomPatterns())
		);
	}

	public AlertProfiles getAlertProfiles()
	{
		return AlertProfiles.fromConfigValue(alertProfiles)
			.replaceMissingCustomPatterns(getCustomPatterns());
	}

	public AlertTriggerSettings getAlertTriggerSettings()
	{
		return AlertTriggerSettings.fromConfigValues(alertTriggerSettings, alertProfiles);
	}

	public boolean isGenericNotificationClickEnabled()
	{
		return clickerGenericNotificationEnabled;
	}

	public ClickerAlertSettings getClickerAlertSettings()
	{
		return ClickerAlertSettings.fromConfigValue(clickerAlertSettings);
	}

	public boolean isAlertClickEnabled(AlertCategory category)
	{
		return getClickerAlertSettings().isEnabled(category);
	}

	public MusicSyncSettings getMusicSyncSettings()
	{
		MusicResponse response;
		try
		{
			response = MusicResponse.valueOf(musicResponse);
		}
		catch (IllegalArgumentException | NullPointerException ignored)
		{
			response = MusicResponse.RHYTHMIC;
		}
		int maximum = clamp(musicMaximumIntensityPercent, 0, 100);
		int minimum = Math.min(clamp(musicMinimumIntensityPercent, 0, 100), maximum);
		return new MusicSyncSettings(
			musicSyncEnabled,
			response,
			clamp(musicSensitivityPercent, 25, 200),
			minimum,
			maximum
		);
	}

	public ClickerSettings getClickerSettings()
	{
		return new ClickerSettings(clickerEnabled, clamp(clickerVolumePercent, 0, 100));
	}

	public ClickerXpSettings getClickerXpSettings()
	{
		return new ClickerXpSettings(
			clickerMinimumXpGain,
			clickerLevelUpEnabled,
			clickerMilestoneEnabled,
			clickerLevel99Enabled
		);
	}

	public ClickerPhraseRules getClickerPhraseRules()
	{
		return ClickerPhraseRules.fromConfigValue(clickerPhraseRules);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof RemoteSettingsSnapshot))
		{
			return false;
		}
		RemoteSettingsSnapshot that = (RemoteSettingsSnapshot) other;
		return schemaVersion == that.schemaVersion
			&& minimumXpGain == that.minimumXpGain
			&& intensityPercent == that.intensityPercent
			&& pulseDurationMillis == that.pulseDurationMillis
			&& levelUpFeedbackEnabled == that.levelUpFeedbackEnabled
			&& milestoneFeedbackEnabled == that.milestoneFeedbackEnabled
			&& level99CelebrationEnabled == that.level99CelebrationEnabled
			&& notificationFeedbackEnabled == that.notificationFeedbackEnabled
			&& notificationIntensityPercent == that.notificationIntensityPercent
			&& notificationDurationMillis == that.notificationDurationMillis
			&& notificationRespectFocus == that.notificationRespectFocus
			&& musicSyncEnabled == that.musicSyncEnabled
			&& musicSensitivityPercent == that.musicSensitivityPercent
			&& musicMinimumIntensityPercent == that.musicMinimumIntensityPercent
			&& musicMaximumIntensityPercent == that.musicMaximumIntensityPercent
			&& clickerEnabled == that.clickerEnabled
			&& clickerVolumePercent == that.clickerVolumePercent
			&& clickerMinimumXpGain == that.clickerMinimumXpGain
			&& clickerLevelUpEnabled == that.clickerLevelUpEnabled
			&& clickerMilestoneEnabled == that.clickerMilestoneEnabled
			&& clickerLevel99Enabled == that.clickerLevel99Enabled
			&& clickerGenericNotificationEnabled == that.clickerGenericNotificationEnabled
			&& Objects.equals(patternPreset, that.patternPreset)
			&& Objects.equals(disabledSkills, that.disabledSkills)
			&& Objects.equals(levelUpPatternPreset, that.levelUpPatternPreset)
			&& Objects.equals(milestonePatternPreset, that.milestonePatternPreset)
			&& Objects.equals(skillFeedbackProfiles, that.skillFeedbackProfiles)
			&& Objects.equals(notificationPatternPreset, that.notificationPatternPreset)
			&& Objects.equals(alertProfiles, that.alertProfiles)
			&& Objects.equals(alertTriggerSettings, that.alertTriggerSettings)
			&& Objects.equals(customPatterns, that.customPatterns)
			&& Objects.equals(musicResponse, that.musicResponse)
			&& Objects.equals(clickerDisabledSkills, that.clickerDisabledSkills)
			&& Objects.equals(clickerAlertSettings, that.clickerAlertSettings)
			&& Objects.equals(clickerPhraseRules, that.clickerPhraseRules);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(
			schemaVersion,
			minimumXpGain,
			intensityPercent,
			pulseDurationMillis,
			patternPreset,
			disabledSkills,
			levelUpFeedbackEnabled,
			levelUpPatternPreset,
			milestoneFeedbackEnabled,
			milestonePatternPreset,
			level99CelebrationEnabled,
			skillFeedbackProfiles,
			notificationFeedbackEnabled,
			notificationIntensityPercent,
			notificationPatternPreset,
			notificationDurationMillis,
			notificationRespectFocus,
			alertProfiles,
			alertTriggerSettings,
			customPatterns,
			musicSyncEnabled,
			musicResponse,
			musicSensitivityPercent,
			musicMinimumIntensityPercent,
			musicMaximumIntensityPercent,
			clickerEnabled,
			clickerVolumePercent,
			clickerMinimumXpGain,
			clickerDisabledSkills,
			clickerLevelUpEnabled,
			clickerMilestoneEnabled,
			clickerLevel99Enabled,
			clickerGenericNotificationEnabled,
			clickerAlertSettings,
			clickerPhraseRules
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
