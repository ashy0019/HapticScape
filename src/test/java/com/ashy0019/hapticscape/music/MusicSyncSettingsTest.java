package com.ashy0019.hapticscape.music;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MusicSyncSettingsTest
{
	@Test
	public void mapsActiveSignalBetweenConfiguredFloorAndCeiling()
	{
		MusicSyncSettings settings = new MusicSyncSettings(
			true,
			MusicResponse.RHYTHMIC,
			100,
			13,
			60
		);

		assertEquals(0.0, settings.mapIntensity(0.0), 0.0001);
		assertEquals(0.365, settings.mapIntensity(0.5), 0.0001);
		assertEquals(0.60, settings.mapIntensity(1.0), 0.0001);
	}

	@Test
	public void sensitivityCanReachCeilingSooner()
	{
		MusicSyncSettings settings = new MusicSyncSettings(
			true,
			MusicResponse.PUNCHY,
			200,
			0,
			80
		);

		assertEquals(0.80, settings.mapIntensity(0.5), 0.0001);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsFloorAboveCeiling()
	{
		new MusicSyncSettings(false, MusicResponse.SMOOTH, 100, 70, 60);
	}
}
