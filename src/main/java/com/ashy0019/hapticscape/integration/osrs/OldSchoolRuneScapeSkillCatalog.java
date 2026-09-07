package com.ashy0019.hapticscape.integration.osrs;

import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.SkillDescriptor;
import java.util.Arrays;

/** Source-neutral Old School RuneScape skill metadata for the standalone host. */
public final class OldSchoolRuneScapeSkillCatalog
{
	private static final SkillCatalog CATALOG = new SkillCatalog(Arrays.asList(
		new SkillDescriptor("attack", "Attack"),
		new SkillDescriptor("defence", "Defence"),
		new SkillDescriptor("strength", "Strength"),
		new SkillDescriptor("hitpoints", "Hitpoints"),
		new SkillDescriptor("ranged", "Ranged"),
		new SkillDescriptor("prayer", "Prayer"),
		new SkillDescriptor("magic", "Magic"),
		new SkillDescriptor("cooking", "Cooking"),
		new SkillDescriptor("woodcutting", "Woodcutting"),
		new SkillDescriptor("fletching", "Fletching"),
		new SkillDescriptor("fishing", "Fishing"),
		new SkillDescriptor("firemaking", "Firemaking"),
		new SkillDescriptor("crafting", "Crafting"),
		new SkillDescriptor("smithing", "Smithing"),
		new SkillDescriptor("mining", "Mining"),
		new SkillDescriptor("herblore", "Herblore"),
		new SkillDescriptor("agility", "Agility"),
		new SkillDescriptor("thieving", "Thieving"),
		new SkillDescriptor("slayer", "Slayer"),
		new SkillDescriptor("farming", "Farming"),
		new SkillDescriptor("runecraft", "Runecraft"),
		new SkillDescriptor("hunter", "Hunter"),
		new SkillDescriptor("construction", "Construction"),
		new SkillDescriptor("sailing", "Sailing")
	));

	private OldSchoolRuneScapeSkillCatalog()
	{
	}

	public static SkillCatalog get()
	{
		return CATALOG;
	}
}
