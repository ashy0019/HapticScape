package com.ashy0019.hapticscape.integration.osrs;

import com.ashy0019.hapticscape.SkillCatalog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OldSchoolRuneScapeSkillCatalogTest
{
	@Test
	public void exposesStandaloneOsrsSkillMetadata()
	{
		SkillCatalog catalog = OldSchoolRuneScapeSkillCatalog.get();
		assertEquals(24, catalog.getSkills().size());
		assertEquals("Attack", catalog.require("attack").getDisplayName());
		assertEquals("Sailing", catalog.require("sailing").getDisplayName());
	}
}
