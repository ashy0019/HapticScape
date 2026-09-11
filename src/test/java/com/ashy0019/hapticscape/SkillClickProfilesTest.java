package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillClickProfilesTest
{
	private static final ClickerXpSettings GLOBAL = new ClickerXpSettings(
		10,
		ClickSequence.ONE,
		ClickSequence.TWO,
		ClickSequence.THREE
	);

	@Test
	public void skillsWithoutOverridesInheritGlobalClickSettings()
	{
		assertEquals(GLOBAL, SkillClickProfiles.empty().resolve("agility", GLOBAL));
	}

	@Test
	public void clickOverridesRoundTripPerSkill()
	{
		ClickerXpSettings ranged = new ClickerXpSettings(
			25,
			ClickSequence.TWO,
			ClickSequence.THREE,
			ClickSequence.ONE
		);
		SkillClickProfiles restored = SkillClickProfiles.fromConfigValue(
			SkillClickProfiles.empty()
				.withOverride("ranged", ranged)
				.toConfigValue()
		);

		assertEquals(ranged, restored.getOverride("RANGED").orElse(null));
		assertEquals(GLOBAL, restored.resolve("cooking", GLOBAL));
	}

	@Test
	public void malformedEntriesAreIgnoredIndependently()
	{
		SkillClickProfiles profiles = SkillClickProfiles.fromConfigValue(
			"v1|RANGED,25,TWO,THREE,ONE"
				+ ";COOKING,nope,ONE,ONE,ONE"
				+ ";AGILITY,10,BOGUS,ONE,ONE"
		);

		assertTrue(profiles.getOverride("ranged").isPresent());
		assertFalse(profiles.getOverride("cooking").isPresent());
		assertFalse(profiles.getOverride("agility").isPresent());
	}

	@Test
	public void removingOverrideReturnsSkillToGlobalAndClearsStorage()
	{
		SkillClickProfiles profiles = SkillClickProfiles.empty()
			.withOverride("ranged", GLOBAL)
			.withoutOverride("ranged");

		assertTrue(profiles.isEmpty());
		assertEquals("", profiles.toConfigValue());
		assertEquals(GLOBAL, profiles.resolve("ranged", GLOBAL));
	}
}
