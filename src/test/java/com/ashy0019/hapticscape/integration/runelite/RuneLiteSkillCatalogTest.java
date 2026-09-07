package com.ashy0019.hapticscape.integration.runelite;

import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuneLiteSkillCatalogTest
{
	@Test
	public void translatesRuneLiteSkillsToNeutralIds()
	{
		assertEquals("cooking", RuneLiteSkillCatalog.skillId(Skill.COOKING));
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
