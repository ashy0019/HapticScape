package com.ashy0019.hapticscape;

import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillSelectionTest
{
	@Test
	public void defaultSelectionEnablesEveryAvailableSkill()
	{
		SkillSelection selection = SkillSelection.fromConfigValue("");

		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			assertTrue(skill.getName(), selection.isEnabled(skill));
		}
	}

	@Test
	public void individualSkillsCanBeDisabledAndEnabledAgain()
	{
		SkillSelection selection = SkillSelection.allEnabled()
			.withEnabled(Skill.AGILITY, false);

		assertFalse(selection.isEnabled(Skill.AGILITY));
		assertTrue(selection.isEnabled(Skill.COOKING));
		assertTrue(selection.withEnabled(Skill.AGILITY, true).isEnabled(Skill.AGILITY));
	}

	@Test
	public void savedSelectionRoundTripsThroughConfiguration()
	{
		SkillSelection selection = SkillSelection.allEnabled()
			.withEnabled(Skill.AGILITY, false)
			.withEnabled(Skill.COOKING, false);

		String configuredValue = selection.toConfigValue();
		SkillSelection restored = SkillSelection.fromConfigValue(configuredValue);

		assertEquals(configuredValue, restored.toConfigValue());
		assertFalse(restored.isEnabled(Skill.AGILITY));
		assertFalse(restored.isEnabled(Skill.COOKING));
	}

	@Test
	public void skillsMissingFromSavedConfigurationDefaultToEnabled()
	{
		SkillSelection restored = SkillSelection.fromConfigValue("AGILITY");

		assertFalse(restored.isEnabled(Skill.AGILITY));
		assertTrue(restored.isEnabled(Skill.COOKING));
	}

	@Test
	public void unknownSavedSkillsAreIgnored()
	{
		SkillSelection restored = SkillSelection.fromConfigValue(
			"AGILITY,FUTURE_SKILL,OVERALL"
		);

		assertEquals("AGILITY", restored.toConfigValue());
	}

	@Test
	public void allAndNoneSelectionsCoverEveryAvailableSkill()
	{
		SkillSelection none = SkillSelection.allEnabled().withAllEnabled(false);

		assertEquals(0, none.getEnabledCount());
		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			assertFalse(skill.getName(), none.isEnabled(skill));
		}
		assertEquals(
			SkillSelection.getSelectableSkills().size(),
			none.withAllEnabled(true).getEnabledCount()
		);
	}
}
