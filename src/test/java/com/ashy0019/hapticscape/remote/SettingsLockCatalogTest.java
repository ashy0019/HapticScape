package com.ashy0019.hapticscape.remote;

import java.util.UUID;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsLockCatalogTest
{
	@Test
	public void blocksCoverOnlyTheirOwnChildren()
	{
		assertTrue(SettingsLockCatalog.covers(
			SettingsLockCatalog.FEEDBACK_BLOCK,
			SettingsLockCatalog.LEVEL_UP_HAPTICS
		));
		assertTrue(SettingsLockCatalog.covers(
			SettingsLockCatalog.profileBlock(Skill.ATTACK),
			SettingsLockCatalog.profileUsesGlobal(Skill.ATTACK)
		));
		assertFalse(SettingsLockCatalog.covers(
			SettingsLockCatalog.profileBlock(Skill.ATTACK),
			SettingsLockCatalog.profileUsesGlobal(Skill.DEFENCE)
		));
		assertFalse(SettingsLockCatalog.covers(
			SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
			SettingsLockCatalog.skillClicks(Skill.ATTACK)
		));
	}

	@Test
	public void dynamicPhraseTargetsRoundTripThroughIds()
	{
		String ruleId = UUID.randomUUID().toString();
		SettingsLockTarget target = SettingsLockCatalog.phraseRule(ruleId);

		assertTrue(SettingsLockCatalog.isPhraseRule(target));
		assertEquals(ruleId, SettingsLockCatalog.phraseRuleId(target));
		assertEquals(target, SettingsLockCatalog.require(target.getId()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void malformedPhraseTargetIsRejected()
	{
		SettingsLockCatalog.require("clicker.phrase.not-a-uuid");
	}
}
