package com.ashy0019.hapticscape.remote;

/**
 * Source-neutral view of the participant settings that Remote Play may capture.
 *
 * <p>The current host supplies these values through its config facade; a
 * standalone HapticScape application can provide the same values from any
 * persistence backend.</p>
 */
public interface RemoteSettingsSource
{
	int minimumXpGain();
	int intensityPercent();
	int pulseDurationMillis();
	String patternPreset();
	String disabledSkills();
	boolean levelUpFeedbackEnabled();
	String levelUpPatternPreset();
	boolean milestoneFeedbackEnabled();
	String milestonePatternPreset();
	boolean level99CelebrationEnabled();
	String skillFeedbackProfiles();
	default String skillClickProfiles() { return ""; }
	boolean notificationFeedbackEnabled();
	int notificationIntensityPercent();
	String notificationPatternPreset();
	int notificationDurationMillis();
	boolean notificationRespectFocus();
	String alertProfiles();
	String alertTriggerSettings();
	String customPatterns();
	boolean musicSyncEnabled();
	String musicResponse();
	int musicSensitivityPercent();
	int musicMinimumIntensityPercent();
	int musicMaximumIntensityPercent();
	boolean clickerEnabled();
	int clickerVolumePercent();
	int clickerMinimumXpGain();
	String clickerDisabledSkills();
	default String clickerXpSequence() { return "ONE"; }
	default String clickerLevelUpSequence() { return clickerLevelUpEnabled() ? "ONE" : "NONE"; }
	default String clickerMilestoneSequence() { return clickerMilestoneEnabled() ? "ONE" : "NONE"; }
	default String clickerGenericNotificationSequence()
	{
		return clickerGenericNotificationEnabled() ? "ONE" : "NONE";
	}
	// Legacy booleans remain available only as migration fallbacks.
	boolean clickerLevelUpEnabled();
	boolean clickerMilestoneEnabled();
	boolean clickerLevel99Enabled();
	boolean clickerGenericNotificationEnabled();
	String clickerAlertSettings();
	String clickerPhraseRules();
	default boolean startWithWindows() { return false; }
	default boolean startMinimized() { return false; }
}
