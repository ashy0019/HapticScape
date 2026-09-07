package com.ashy0019.hapticscape;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SkillCatalogTest
{
	@Test
	public void canonicalIdsAndDisplayNamesArePreserved()
	{
		SkillCatalog catalog = new SkillCatalog(Arrays.asList(
			new SkillDescriptor("ATTACK", "Attack"),
			new SkillDescriptor("woodcutting", "Woodcutting")
		));

		assertEquals(Arrays.asList("attack", "woodcutting"), catalog.getSkillIds());
		assertEquals("Attack", catalog.require("attack").getDisplayName());
		assertEquals("Woodcutting", catalog.require("WOODCUTTING").getDisplayName());
	}

	@Test(expected = IllegalArgumentException.class)
	public void duplicateCanonicalIdsAreRejected()
	{
		new SkillCatalog(Arrays.asList(
			new SkillDescriptor("Attack", "Attack"),
			new SkillDescriptor("ATTACK", "Attack Again")
		));
	}
}
