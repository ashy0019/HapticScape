package com.ashy0019.hapticscape.update;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateVersionTest
{
	@Test
	public void comparesStableNumericVersions()
	{
		assertTrue(UpdateVersion.isNewer("1.6.1", "1.6.0"));
		assertTrue(UpdateVersion.isNewer("v2.0.0", "1.99.99"));
		assertFalse(UpdateVersion.isNewer("1.6.0", "1.6.0"));
		assertFalse(UpdateVersion.isNewer("1.5.9", "1.6.0"));
	}

	@Test
	public void rejectsPrereleaseAndMalformedVersions()
	{
		assertFalse(UpdateVersion.parse("1.7.0-beta").isPresent());
		assertFalse(UpdateVersion.parse("development").isPresent());
		assertFalse(UpdateVersion.parse("1..7").isPresent());
	}
}
