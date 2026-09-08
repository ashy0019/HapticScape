package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickerPhraseMatchMode;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRule;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.event.ChatEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatOutputDecisionTest
{
	@Test
	public void ordinaryChatCanTriggerPhraseClick()
	{
		ChatOutputDecision decision = ChatOutputDecision.classify(
			new ChatEvent(
				"test-source",
				ChatEvent.Kind.OTHER,
				"<col=ffffff>Hello world</col>",
				"Hello world"
			),
			phraseRules("hello")
		);

		assertTrue(decision.shouldClick());
		assertFalse(decision.hasSpecificAlert());
	}

	@Test
	public void directMessageWithPhraseProducesOneClickDecisionAndAlert()
	{
		ChatOutputDecision decision = ChatOutputDecision.classify(
			new ChatEvent(
				"test-source",
				ChatEvent.Kind.DIRECT_MESSAGE,
				"Hello there",
				"Hello there"
			),
			phraseRules("hello")
		);

		assertTrue(decision.shouldClick());
		assertTrue(decision.hasSpecificAlert());
		assertEquals(AlertCategory.DIRECT_MESSAGE, decision.getSpecificAlert());
	}

	@Test
	public void tradeRequestWithoutPhraseProducesOnlySpecificAlert()
	{
		ChatOutputDecision decision = ChatOutputDecision.classify(
			new ChatEvent(
				"test-source",
				ChatEvent.Kind.TRADE_REQUEST,
				"Someone wishes to trade with you.",
				"Someone wishes to trade with you."
			),
			phraseRules("unrelated")
		);

		assertFalse(decision.shouldClick());
		assertTrue(decision.hasSpecificAlert());
		assertEquals(AlertCategory.TRADE_REQUEST, decision.getSpecificAlert());
	}

	private static ClickerPhraseRules phraseRules(String phrase)
	{
		return ClickerPhraseRules.empty().withAdded(
			new ClickerPhraseRule(
				true,
				ClickerPhraseMatchMode.CONTAINS,
				phrase
			)
		);
	}
}
