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
}