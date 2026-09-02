package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AlertTriggerSettingsTest
{
	@Test
	public void defaultsCoverEveryParameterizedAlert()
	{
		AlertTriggerSettings settings = AlertTriggerSettings.defaults();

		assertEquals(20, settings.get(AlertCategory.LOW_HITPOINTS));
		assertEquals(10, settings.get(AlertCategory.LOW_PRAYER));
		assertEquals(100_000, settings.get(AlertCategory.VALUABLE_DROP));
		assertEquals(100, settings.get(AlertCategory.SPECIAL_ATTACK_READY));
	}

	@Test
	public void roundTripPreservesIndependentTriggerValues()
	{
		AlertTriggerSettings original = AlertTriggerSettings.defaults()
			.withValue(AlertCategory.LOW_HITPOINTS, 31)
			.withValue(AlertCategory.VALUABLE_DROP, 2_500_000)
			.withValue(AlertCategory.SPECIAL_ATTACK_READY, 75);
		AlertTriggerSettings restored = AlertTriggerSettings.fromConfigValues(
			original.toConfigValue(),
			""
		);

		assertEquals(31, restored.get(AlertCategory.LOW_HITPOINTS));
		assertEquals(2_500_000, restored.get(AlertCategory.VALUABLE_DROP));
		assertEquals(75, restored.get(AlertCategory.SPECIAL_ATTACK_READY));
		assertTrue(restored.toConfigValue().startsWith("v1|"));
	}

	@Test
	public void legacyProfileThresholdsMigrateOnce()
	{
		String legacy = "v1|LOW_HITPOINTS,CUSTOM,60,500,DOUBLE,27;"
			+ "LOW_PRAYER,OFF,60,500,DOUBLE,13";
		AlertTriggerSettings settings = AlertTriggerSettings.fromConfigValues("", legacy);

		assertEquals(27, settings.get(AlertCategory.LOW_HITPOINTS));
		assertEquals(13, settings.get(AlertCategory.LOW_PRAYER));
	}

	@Test
	public void malformedValuesKeepSafeDefaults()
	{
		AlertTriggerSettings settings = AlertTriggerSettings.fromConfigValues(
			"v1|LOW_HITPOINTS=nope;VALUABLE_DROP=-1;SPECIAL_ATTACK_READY=900",
			""
		);

		assertEquals(20, settings.get(AlertCategory.LOW_HITPOINTS));
		assertEquals(100_000, settings.get(AlertCategory.VALUABLE_DROP));
		assertEquals(100, settings.get(AlertCategory.SPECIAL_ATTACK_READY));
	}
}
