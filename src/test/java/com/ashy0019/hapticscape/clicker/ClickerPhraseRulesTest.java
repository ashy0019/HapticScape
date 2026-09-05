package com.ashy0019.hapticscape.clicker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickerPhraseRulesTest
{
	@Test
	public void anyMatchingRuleTriggers()
	{
		ClickerPhraseRules rules = ClickerPhraseRules.empty()
			.withAdded(new ClickerPhraseRule(
				true,
				ClickerPhraseMatchMode.CONTAINS,
				"bird nest"
			))
			.withAdded(new ClickerPhraseRule(
				true,
				ClickerPhraseMatchMode.EXACT,
				"Hello"
			));

		assertTrue(rules.matches("You receive a bird nest."));
		assertTrue(rules.matches("HELLO"));
		assertFalse(rules.matches("Nothing happened."));
	}

	@Test
	public void configurationRoundTripPreservesRegexCharacters()
	{
		ClickerPhraseRules original = ClickerPhraseRules.empty()
			.withAdded(new ClickerPhraseRule(
				true,
				ClickerPhraseMatchMode.REGEX,
				"(?i)^You receive \\d+ x (rune|coin); nice,$"
			))
			.withAdded(new ClickerPhraseRule(
				false,
				ClickerPhraseMatchMode.CONTAINS,
				"ã“ã‚“ã«ã¡ã¯"
			));

		ClickerPhraseRules restored =
			ClickerPhraseRules.fromConfigValue(
				original.toConfigValue()
			);

		assertEquals(original, restored);
	}

	@Test
	public void malformedEntriesAreIgnored()
	{
		ClickerPhraseRules restored =
			ClickerPhraseRules.fromConfigValue(
				"v1;1,CONTAINS,aGVsbG8;"
					+ "1,REGEX,%%%BADBASE64%%%;"
					+ "x,EXACT,aGVsbG8"
			);

		assertEquals(1, restored.getRules().size());
		assertTrue(restored.matches("well hello there"));
	}

	@Test
	public void unknownStorageVersionFailsSafe()
	{
		ClickerPhraseRules restored =
			ClickerPhraseRules.fromConfigValue(
				"v999;1,CONTAINS,aGVsbG8"
			);

		assertTrue(restored.getRules().isEmpty());
	}

	@Test
	public void legacyRulesReceiveStableIdsAndMigrateToVersionTwo()
	{
		String legacy = "v1;1,CONTAINS,aGVsbG8;1,CONTAINS,aGVsbG8";
		ClickerPhraseRules first = ClickerPhraseRules.fromConfigValue(legacy);
		ClickerPhraseRules second = ClickerPhraseRules.fromConfigValue(legacy);

		assertTrue(ClickerPhraseRules.requiresMigration(legacy));
		assertEquals(first, second);
		assertFalse(first.getRules().get(0).getId().equals(
			first.getRules().get(1).getId()
		));
		assertTrue(first.toConfigValue().startsWith("v2;"));
		assertEquals(first, ClickerPhraseRules.fromConfigValue(first.toConfigValue()));
	}

	@Test
	public void editingRulePreservesItsIdentity()
	{
		ClickerPhraseRule original = new ClickerPhraseRule(
			true,
			ClickerPhraseMatchMode.CONTAINS,
			"hello"
		);
		ClickerPhraseRule edited = original.withValues(
			false,
			ClickerPhraseMatchMode.EXACT,
			"goodbye"
		);

		assertEquals(original.getId(), edited.getId());
		assertFalse(original.equals(edited));
	}
}
