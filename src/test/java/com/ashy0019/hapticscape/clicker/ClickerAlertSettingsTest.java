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
			assertFalse(category.name(), settings.isEnabled(category));
		}
	}

	@Test
	public void selectionsRoundTripInStableCategoryOrder()
	{
		ClickerAlertSettings settings = ClickerAlertSettings.noneEnabled()
			.withEnabled(AlertCategory.PLAYER_DEATH, true)
			.withEnabled(AlertCategory.DIRECT_MESSAGE, true);

		String configured = settings.toConfigValue();
		ClickerAlertSettings restored = ClickerAlertSettings.fromConfigValue(configured);

		assertEquals("DIRECT_MESSAGE,PLAYER_DEATH", configured);
		assertTrue(restored.isEnabled(AlertCategory.DIRECT_MESSAGE));
		assertTrue(restored.isEnabled(AlertCategory.PLAYER_DEATH));
		assertFalse(restored.isEnabled(AlertCategory.TRADE_REQUEST));
	}

	@Test
	public void categoriesCanBeDisabledAgain()
	{
		ClickerAlertSettings settings = ClickerAlertSettings.noneEnabled()
			.withEnabled(AlertCategory.LOW_HITPOINTS, true)
			.withEnabled(AlertCategory.LOW_HITPOINTS, false);

		assertFalse(settings.isEnabled(AlertCategory.LOW_HITPOINTS));
		assertEquals("", settings.toConfigValue());
	}

	@Test
	public void malformedAndFutureCategoriesAreIgnored()
	{
		ClickerAlertSettings settings = ClickerAlertSettings.fromConfigValue(
			"DIRECT_MESSAGE,NOT_A_REAL_ALERT,,TRADE_REQUEST"
		);

		assertTrue(settings.isEnabled(AlertCategory.DIRECT_MESSAGE));
		assertTrue(settings.isEnabled(AlertCategory.TRADE_REQUEST));
		assertEquals("DIRECT_MESSAGE,TRADE_REQUEST", settings.toConfigValue());
	}
}
