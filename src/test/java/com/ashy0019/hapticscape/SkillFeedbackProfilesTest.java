package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillFeedbackProfilesTest
{
	private static final XpFeedbackSettings GLOBAL = new XpFeedbackSettings(
		1,
		50,
		500,
		HapticPatternSelection.SINGLE
	);

	@Test
	public void skillsWithoutOverridesInheritGlobalSettings()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.empty();

		assertEquals(GLOBAL, profiles.resolve("agility", GLOBAL));
	}

	@Test
	public void overridesOnlyAffectTheirSelectedSkill()
	{
		XpFeedbackSettings agility = new XpFeedbackSettings(
			100,
			75,
			900,
			HapticPatternSelection.TRIPLE
		);
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.empty()
			.withOverride("agility", agility);

		assertEquals(agility, profiles.resolve("AGILITY", GLOBAL));
		assertEquals(GLOBAL, profiles.resolve("cooking", GLOBAL));
	}

	@Test
	public void savedProfilesRoundTripWithoutLosingSettings()
	{
		XpFeedbackSettings agility = new XpFeedbackSettings(
			100,
			75,
			900,
			HapticPatternSelection.TRIPLE
		);
		XpFeedbackSettings cooking = new XpFeedbackSettings(
			25,
			40,
			350,
			HapticPatternSelection.DOUBLE
		);
		SkillFeedbackProfiles original = SkillFeedbackProfiles.empty()
			.withOverride("agility", agility)
			.withOverride("cooking", cooking);

		SkillFeedbackProfiles restored = SkillFeedbackProfiles.fromConfigValue(
			original.toConfigValue()
		);

		assertEquals(agility, restored.getOverride("agility").orElse(null));
		assertEquals(cooking, restored.getOverride("cooking").orElse(null));
	}

	@Test
	public void customPatternIdsRoundTrip()
	{
		XpFeedbackSettings custom = new XpFeedbackSettings(
			100,
			75,
			900,
			HapticPatternSelection.custom(42)
		);
		SkillFeedbackProfiles restored = SkillFeedbackProfiles.fromConfigValue(
			SkillFeedbackProfiles.empty()
				.withOverride("agility", custom)
				.toConfigValue()
		);

		assertEquals(custom, restored.getOverride("agility").orElse(null));
	}

	@Test
	public void deletedCustomPatternsFallBackToSinglePulse()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.empty()
			.withOverride(
				"agility",
				new XpFeedbackSettings(
					100,
					75,
					900,
					HapticPatternSelection.custom(99)
				)
			)
			.replaceMissingCustomPatterns(CustomPatternLibrary.defaults());

		assertEquals(
			HapticPatternSelection.SINGLE,
			profiles.getOverride("agility").get().getPatternSelection()
		);
	}

	@Test
	public void malformedEntriesAreIgnoredIndependentlyAndUnknownIdsSurvive()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.fromConfigValue(
			"v1|AGILITY,100,75,900,TRIPLE"
				+ ";COOKING,not-a-number,40,350,DOUBLE"
				+ ";FUTURE_SKILL,10,50,500,SINGLE"
				+ ";COOKING,10,101,500,SINGLE"
		);

		assertTrue(profiles.getOverride("agility").isPresent());
		assertTrue(profiles.getOverride("future_skill").isPresent());
		assertFalse(profiles.getOverride("cooking").isPresent());
	}

	@Test
	public void unsupportedStorageVersionFallsBackToGlobalSettings()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.fromConfigValue(
			"v2|AGILITY,100,75,900,TRIPLE"
		);

		assertTrue(profiles.isEmpty());
		assertEquals(GLOBAL, profiles.resolve("agility", GLOBAL));
	}

	@Test
	public void removingOverrideReturnsSkillToGlobalAndClearsStorage()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.empty()
			.withOverride(
				"agility",
				new XpFeedbackSettings(100, 75, 900, HapticPatternSelection.TRIPLE)
			)
			.withoutOverride("agility");

		assertTrue(profiles.isEmpty());
		assertEquals("", profiles.toConfigValue());
		assertEquals(GLOBAL, profiles.resolve("agility", GLOBAL));
	}
}
