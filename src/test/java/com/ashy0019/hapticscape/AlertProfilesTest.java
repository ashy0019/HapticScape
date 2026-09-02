package com.ashy0019.hapticscape;

import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlertProfilesTest
{
	private static final NotificationFeedbackSettings GENERIC =
		new NotificationFeedbackSettings(
			true,
			true,
			50,
			500,
			HapticPatternSelection.DOUBLE
		);

	@Test
	public void socialCategoriesUseGenericByDefault()
	{
		AlertProfiles profiles = AlertProfiles.defaults();

		assertEquals(
			HapticPatternSelection.DOUBLE,
			profiles.resolve(AlertCategory.DIRECT_MESSAGE, GENERIC)
				.get().getPatternSelection()
		);
		assertEquals(
			HapticPatternSelection.DOUBLE,
			profiles.resolve(AlertCategory.TRADE_REQUEST, GENERIC)
				.get().getPatternSelection()
		);
	}

	@Test
	public void lowStatusCategoriesAreOffByDefault()
	{
		AlertProfiles profiles = AlertProfiles.defaults();

		assertFalse(profiles.resolve(AlertCategory.LOW_HITPOINTS, GENERIC).isPresent());
		assertFalse(profiles.resolve(AlertCategory.LOW_PRAYER, GENERIC).isPresent());
	}

	@Test
	public void customProfileOverridesGenericProfile()
	{
		AlertProfile custom = new AlertProfile(
			AlertBehavior.CUSTOM,
			77,
			900,
			HapticPatternSelection.TRIPLE,
			12
		);
		AlertPlayback playback = AlertProfiles.defaults()
			.withProfile(AlertCategory.LOW_PRAYER, custom)
			.resolve(AlertCategory.LOW_PRAYER, GENERIC)
			.get();

		assertEquals(77, playback.getIntensityPercent());
		assertEquals(900, playback.getDurationMillis());
		assertEquals(HapticPatternSelection.TRIPLE, playback.getPatternSelection());
	}

	@Test
	public void disabledMasterSwitchSuppressesEveryCategory()
	{
		NotificationFeedbackSettings disabled = new NotificationFeedbackSettings(
			false,
			true,
			50,
			500,
			HapticPatternSelection.DOUBLE
		);

		for (AlertCategory category : AlertCategory.values())
		{
			assertEquals(Optional.empty(), AlertProfiles.defaults().resolve(category, disabled));
		}
	}

	@Test
	public void profilesRoundTripWithBehaviorThresholdAndCustomPattern()
	{
		AlertProfile original = new AlertProfile(
			AlertBehavior.CUSTOM,
			63,
			1_250,
			HapticPatternSelection.custom(42),
			17
		);
		AlertProfiles restored = AlertProfiles.fromConfigValue(
			AlertProfiles.defaults()
				.withProfile(AlertCategory.LOW_HITPOINTS, original)
				.toConfigValue()
		);
		AlertProfile profile = restored.get(AlertCategory.LOW_HITPOINTS);

		assertEquals(AlertBehavior.CUSTOM, profile.getBehavior());
		assertEquals(63, profile.getIntensityPercent());
		assertEquals(1_250, profile.getDurationMillis());
		assertEquals(HapticPatternSelection.custom(42), profile.getPatternSelection());
		assertEquals(17, profile.getThreshold());
	}

	@Test
	public void malformedEntryKeepsItsSafeDefault()
	{
		AlertProfiles profiles = AlertProfiles.fromConfigValue(
			"v1|LOW_HITPOINTS,CUSTOM,999,500,SINGLE,20"
		);

		assertEquals(AlertBehavior.OFF, profiles.get(AlertCategory.LOW_HITPOINTS).getBehavior());
		assertTrue(profiles.get(AlertCategory.DIRECT_MESSAGE) != null);
	}
}
