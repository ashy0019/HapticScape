package com.ashy0019.hapticscape.integration.runelite;

import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuneLiteSkillCatalogTest
{
	@Test
	public void roundTripsNeutralSkillIdsAtTheRuneLiteEdge()
	{
		assertEquals("cooking", RuneLiteSkillCatalog.skillId(Skill.COOKING));
		assertEquals(Skill.COOKING, RuneLiteSkillCatalog.skill("COOKING"));
		assertEquals(Skill.COOKING, RuneLiteSkillCatalog.skill("cooking"));
	}

	@Test
	public void neutralCatalogUsesRuneLiteDisplayNames()
	{
		assertEquals(
			Skill.ATTACK.getName(),
			RuneLiteSkillCatalog.getNeutralCatalog().require("attack").getDisplayName()
		);
	}
}
