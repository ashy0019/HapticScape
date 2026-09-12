package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.AlertCategory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickerAlertSettingsTest
{
	@Test
	public void alertsAreOptInByDefault()
	{
		ClickerAlertSettings settings = ClickerAlertSettings.fromConfigValue("");

		for (AlertCategory category : AlertCategory.values())
		{
			assertEquals(category.name(), ClickSequence.NONE, settings.getSequence(category));
		}
	}

	@Test
	public void boundedSequencesRoundTripInStableCategoryOrder()
	{
		ClickerAlertSettings settings = ClickerAlertSettings.noneEnabled()
			.withSequence(AlertCategory.PLAYER_DEATH, ClickSequence.THREE)
			.withSequence(AlertCategory.DIRECT_MESSAGE, ClickSequence.TWO);

		String configured = settings.toConfigValue();
		ClickerAlertSettings restored = ClickerAlertSettings.fromConfigValue(configured);

		assertEquals("v2;DIRECT_MESSAGE=TWO;PLAYER_DEATH=THREE", configured);
		assertEquals(ClickSequence.TWO, restored.getSequence(AlertCategory.DIRECT_MESSAGE));
		assertEquals(ClickSequence.THREE, restored.getSequence(AlertCategory.PLAYER_DEATH));
		assertEquals(ClickSequence.NONE, restored.getSequence(AlertCategory.TRADE_REQUEST));
	}

	@Test
	public void legacyEnabledCategoriesMigrateToOneClick()
	{
		String legacy = "DIRECT_MESSAGE,NOT_A_REAL_ALERT,,TRADE_REQUEST";
		ClickerAlertSettings settings = ClickerAlertSettings.fromConfigValue(legacy);

		assertTrue(ClickerAlertSettings.requiresMigration(legacy));
		assertEquals(ClickSequence.ONE, settings.getSequence(AlertCategory.DIRECT_MESSAGE));
		assertEquals(ClickSequence.ONE, settings.getSequence(AlertCategory.TRADE_REQUEST));
		assertEquals("v2;DIRECT_MESSAGE=ONE;TRADE_REQUEST=ONE", settings.toConfigValue());
	}

	@Test
	public void checkboxCompatibilityMapsToOneOrNone()
	{
		ClickerAlertSettings settings = ClickerAlertSettings.noneEnabled()
			.withEnabled(AlertCategory.LOW_HITPOINTS, true);

		assertTrue(settings.isEnabled(AlertCategory.LOW_HITPOINTS));
		assertEquals(ClickSequence.ONE, settings.getSequence(AlertCategory.LOW_HITPOINTS));
		settings = settings.withEnabled(AlertCategory.LOW_HITPOINTS, false);
		assertFalse(settings.isEnabled(AlertCategory.LOW_HITPOINTS));
		assertEquals("", settings.toConfigValue());
	}
}
