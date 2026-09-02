package com.ashy0019.hapticscape.clicker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClickerSettingsTest
{
	@Test
	public void clampsVolumeToUserFacingRange()
	{
		assertEquals(0, new ClickerSettings(true, -1).getVolumePercent());
		assertEquals(100, new ClickerSettings(true, 101).getVolumePercent());
	}

	@Test
	public void convertsLinearPercentageToDecibelGain()
	{
		assertEquals(0.0f, new ClickerSettings(true, 100).getGainDb(), 0.001f);
		assertEquals(-3.098f, new ClickerSettings(true, 70).getGainDb(), 0.001f);
	}

	@Test(expected = IllegalStateException.class)
	public void zeroVolumeHasNoDecibelValue()
	{
		new ClickerSettings(true, 0).getGainDb();
	}
}
