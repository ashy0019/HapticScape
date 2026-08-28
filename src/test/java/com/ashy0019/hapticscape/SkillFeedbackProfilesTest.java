package com.ashy0019.hapticscape;

import net.runelite.api.Skill;
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

		assertEquals(GLOBAL, profiles.resolve(Skill.AGILITY, GLOBAL));
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
			.withOverride(Skill.AGILITY, agility);

		assertEquals(agility, profiles.resolve(Skill.AGILITY, GLOBAL));
		assertEquals(GLOBAL, profiles.resolve(Skill.COOKING, GLOBAL));
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
			.withOverride(Skill.AGILITY, agility)
			.withOverride(Skill.COOKING, cooking);

		SkillFeedbackProfiles restored = SkillFeedbackProfiles.fromConfigValue(
			original.toConfigValue()
		);

		assertEquals(agility, restored.getOverride(Skill.AGILITY).orElse(null));
		assertEquals(cooking, restored.getOverride(Skill.COOKING).orElse(null));
	}

	@Test
	public void malformedAndUnknownEntriesAreIgnoredIndependently()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.fromConfigValue(
			"v1|AGILITY,100,75,900,TRIPLE"
				+ ";COOKING,not-a-number,40,350,DOUBLE"
				+ ";UNKNOWN_SKILL,10,50,500,SINGLE"
				+ ";COOKING,10,101,500,SINGLE"
		);

		assertTrue(profiles.getOverride(Skill.AGILITY).isPresent());
		assertFalse(profiles.getOverride(Skill.COOKING).isPresent());
	}

	@Test
	public void unsupportedStorageVersionFallsBackToGlobalSettings()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.fromConfigValue(
			"v2|AGILITY,100,75,900,TRIPLE"
		);

		assertTrue(profiles.isEmpty());
		assertEquals(GLOBAL, profiles.resolve(Skill.AGILITY, GLOBAL));
	}

	@Test
	public void removingOverrideReturnsSkillToGlobalAndClearsStorage()
	{
		SkillFeedbackProfiles profiles = SkillFeedbackProfiles.empty()
			.withOverride(
				Skill.AGILITY,
				new XpFeedbackSettings(100, 75, 900, HapticPatternSelection.TRIPLE)
			)
			.withoutOverride(Skill.AGILITY);

		assertTrue(profiles.isEmpty());
		assertEquals("", profiles.toConfigValue());
		assertEquals(GLOBAL, profiles.resolve(Skill.AGILITY, GLOBAL));
	}
}
