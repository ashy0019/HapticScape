package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.integration.osrs.OldSchoolRuneScapeSkillCatalog;

/** Mutable-by-subclass source-neutral settings fixture with application defaults. */
public class TestHapticScapeSettings implements HapticScapeSettingsSource
{
	@Override public String intifaceServer() { return "ws://localhost:12345"; }
	@Override public int minimumXpGain() { return 1; }
	@Override public int intensityPercent() { return 50; }
	@Override public int pulseDurationMillis() { return 500; }
	@Override public String patternPreset() { return HapticPatternSelection.SINGLE.toConfigValue(); }
	@Override public String disabledSkills() { return ""; }
	@Override public boolean levelUpFeedbackEnabled() { return true; }
	@Override public String levelUpPatternPreset() { return HapticPatternSelection.DOUBLE.toConfigValue(); }
	@Override public boolean milestoneFeedbackEnabled() { return true; }
	@Override public String milestonePatternPreset() { return HapticPatternSelection.TRIPLE.toConfigValue(); }
	@Override public boolean level99CelebrationEnabled() { return true; }
	@Override public String skillFeedbackProfiles() { return ""; }
	@Override public boolean notificationFeedbackEnabled() { return false; }
	@Override public int notificationIntensityPercent() { return 50; }
	@Override public String notificationPatternPreset() { return HapticPatternSelection.DOUBLE.toConfigValue(); }
	@Override public int notificationDurationMillis() { return 500; }
	@Override public boolean notificationRespectFocus() { return true; }
	@Override public String alertProfiles() { return ""; }
	@Override public String alertTriggerSettings() { return ""; }
	@Override public String customPatterns() { return ""; }
	@Override public boolean musicSyncEnabled() { return false; }
	@Override public String musicResponse() { return "RHYTHMIC"; }
	@Override public int musicSensitivityPercent() { return 100; }
	@Override public int musicMinimumIntensityPercent() { return 0; }
	@Override public int musicMaximumIntensityPercent() { return 60; }
	@Override public boolean clickerEnabled() { return false; }
	@Override public int clickerVolumePercent() { return 70; }
	@Override public int clickerMinimumXpGain() { return 1; }
	@Override
	public String clickerDisabledSkills()
	{
		return SkillSelection.allEnabled()
			.withAllEnabled(OldSchoolRuneScapeSkillCatalog.get().getSkillIds(), false)
			.toConfigValue();
	}
	@Override public String clickerXpSequence() { return "ONE"; }
	@Override public String clickerLevelUpSequence() { return "ONE"; }
	@Override public String clickerMilestoneSequence() { return "ONE"; }
	@Override public String clickerGenericNotificationSequence() { return "NONE"; }
	@Override public boolean clickerLevelUpEnabled() { return true; }
	@Override public boolean clickerMilestoneEnabled() { return true; }
	@Override public boolean clickerLevel99Enabled() { return true; }
	@Override public boolean clickerGenericNotificationEnabled() { return false; }
	@Override public String clickerAlertSettings() { return ""; }
	@Override public String clickerPhraseRules() { return ""; }
	@Override public String remoteRelayUrl() { return DEFAULT_REMOTE_RELAY_URL; }
	@Override public boolean remoteSettingsAllowed() { return true; }
	@Override public boolean remoteHapticsAllowed() { return true; }
	@Override public boolean remoteLiveHapticsAllowed() { return false; }
	@Override public boolean remoteClicksAllowed() { return true; }
	@Override public boolean remoteDesktopNotificationsAllowed() { return true; }
	@Override public boolean remoteLocalChatboxMessagesAllowed() { return false; }
	@Override public boolean remoteProtectedExitAllowed() { return false; }
	@Override public int remoteMaximumIntensityPercent() { return 60; }
	@Override public int remoteMaximumDurationMillis() { return 3_000; }
	@Override public int remoteMaximumLiveDurationMillis() { return 30_000; }
}
