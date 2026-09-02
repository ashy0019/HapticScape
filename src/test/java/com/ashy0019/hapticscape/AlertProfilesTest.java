package com.ashy0019.hapticscape;

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
			HapticPatternSelection.TRIPLE
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
	public void useGenericInheritsProfileEvenWhenCatchAllIsDisabled()
	{
		NotificationFeedbackSettings disabled = new NotificationFeedbackSettings(
			false,
			true,
			50,
			500,
			HapticPatternSelection.DOUBLE
		);

		AlertPlayback playback = AlertProfiles.defaults()
			.resolve(AlertCategory.DIRECT_MESSAGE, disabled)
			.get();

		assertEquals(50, playback.getIntensityPercent());
		assertEquals(HapticPatternSelection.DOUBLE, playback.getPatternSelection());
	}

	@Test
	public void profilesRoundTripWithBehaviorAndCustomPattern()
	{
		AlertProfile original = new AlertProfile(
			AlertBehavior.CUSTOM,
			63,
			1_250,
			HapticPatternSelection.custom(42)
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
		assertTrue(restored.toConfigValue().startsWith("v2|"));
	}

	@Test
	public void legacyProfilesStillLoadTheirOutputSettings()
	{
		AlertProfiles profiles = AlertProfiles.fromConfigValue(
			"v1|LOW_HITPOINTS,CUSTOM,63,1250,TRIPLE,17"
		);
		AlertProfile profile = profiles.get(AlertCategory.LOW_HITPOINTS);

		assertEquals(AlertBehavior.CUSTOM, profile.getBehavior());
		assertEquals(63, profile.getIntensityPercent());
		assertEquals(1_250, profile.getDurationMillis());
		assertEquals(HapticPatternSelection.TRIPLE, profile.getPatternSelection());
	}

	@Test
	public void newCategoriesAreOffByDefault()
	{
		AlertProfiles profiles = AlertProfiles.defaults();

		assertEquals(AlertBehavior.OFF, profiles.get(AlertCategory.VALUABLE_DROP).getBehavior());
		assertEquals(AlertBehavior.OFF, profiles.get(AlertCategory.INVENTORY_FULL).getBehavior());
		assertEquals(AlertBehavior.OFF, profiles.get(AlertCategory.POISONED_OR_VENOMED).getBehavior());
		assertEquals(AlertBehavior.OFF, profiles.get(AlertCategory.SPECIAL_ATTACK_READY).getBehavior());
		assertEquals(AlertBehavior.OFF, profiles.get(AlertCategory.PLAYER_DEATH).getBehavior());
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
