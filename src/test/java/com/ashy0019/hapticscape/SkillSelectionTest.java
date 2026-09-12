package com.ashy0019.hapticscape;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillSelectionTest
{
	private static final List<String> SKILLS = Arrays.asList("agility", "cooking");

	@Test
	public void defaultSelectionEnablesEverySuppliedSkill()
	{
		SkillSelection selection = SkillSelection.fromConfigValue("");

		for (String skillId : SKILLS)
		{
			assertTrue(skillId, selection.isEnabled(skillId));
		}
	}

	@Test
	public void individualSkillsCanBeDisabledAndEnabledAgain()
	{
		SkillSelection selection = SkillSelection.allEnabled()
			.withEnabled("agility", false);

		assertFalse(selection.isEnabled("AGILITY"));
		assertTrue(selection.isEnabled("cooking"));
		assertTrue(selection.withEnabled("Agility", true).isEnabled("agility"));
	}

	@Test
	public void savedSelectionRoundTripsThroughConfiguration()
	{
		SkillSelection selection = SkillSelection.allEnabled()
			.withEnabled("agility", false)
			.withEnabled("cooking", false);

		String configuredValue = selection.toConfigValue();
		SkillSelection restored = SkillSelection.fromConfigValue(configuredValue);

		assertEquals("AGILITY,COOKING", configuredValue);
		assertEquals(configuredValue, restored.toConfigValue());
		assertFalse(restored.isEnabled("agility"));
		assertFalse(restored.isEnabled("cooking"));
	}

	@Test
	public void skillsMissingFromSavedConfigurationDefaultToEnabled()
	{
		SkillSelection restored = SkillSelection.fromConfigValue("AGILITY");

		assertFalse(restored.isEnabled("agility"));
		assertTrue(restored.isEnabled("cooking"));
	}

	@Test
	public void unknownSavedSkillIdsArePreservedForForwardCompatibility()
	{
		SkillSelection restored = SkillSelection.fromConfigValue(
			"AGILITY,FUTURE_SKILL"
		);

		assertEquals("AGILITY,FUTURE_SKILL", restored.toConfigValue());
		assertFalse(restored.isEnabled("future_skill"));
	}

	@Test
	public void allAndNoneSelectionsUseAnExternalSkillCatalog()
	{
		SkillSelection none = SkillSelection.allEnabled().withAllEnabled(SKILLS, false);

		assertEquals(0, none.getEnabledCount(SKILLS));
		for (String skillId : SKILLS)
		{
			assertFalse(skillId, none.isEnabled(skillId));
		}
		assertEquals(
			SKILLS.size(),
			none.withAllEnabled(SKILLS, true).getEnabledCount(SKILLS)
		);
	}
}
