package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerAlertSettings;
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
	public void ordinaryChatCanTriggerPhraseSequence()
	{
		ChatOutputDecision decision = ChatOutputDecision.classify(
			new ChatEvent(
				"test-source",
				ChatEvent.Kind.OTHER,
				"<col=ffffff>Hello world</col>",
				"Hello world"
			),
			phraseRules("hello", ClickSequence.TWO),
			ClickerAlertSettings.noneEnabled()
		);

		assertEquals(ClickSequence.TWO, decision.getClickSequence());
		assertTrue(decision.shouldClick());
		assertFalse(decision.hasSpecificAlert());
	}

	@Test
	public void directMessageAndPhraseResolveToStrongestSingleSequence()
	{
		ClickerAlertSettings alerts = ClickerAlertSettings.noneEnabled()
			.withSequence(AlertCategory.DIRECT_MESSAGE, ClickSequence.TWO);
		ChatOutputDecision decision = ChatOutputDecision.classify(
			new ChatEvent(
				"test-source",
				ChatEvent.Kind.DIRECT_MESSAGE,
				"Hello there",
				"Hello there"
			),
			phraseRules("hello", ClickSequence.THREE),
			alerts
		);

		assertEquals(ClickSequence.THREE, decision.getClickSequence());
		assertTrue(decision.hasSpecificAlert());
		assertEquals(AlertCategory.DIRECT_MESSAGE, decision.getSpecificAlert());
	}

	@Test
	public void tradeRequestWithoutPhraseUsesAlertSequence()
	{
		ClickerAlertSettings alerts = ClickerAlertSettings.noneEnabled()
			.withSequence(AlertCategory.TRADE_REQUEST, ClickSequence.TWO);
		ChatOutputDecision decision = ChatOutputDecision.classify(
			new ChatEvent(
				"test-source",
				ChatEvent.Kind.TRADE_REQUEST,
				"Someone wishes to trade with you.",
				"Someone wishes to trade with you."
			),
			phraseRules("unrelated", ClickSequence.THREE),
			alerts
		);

		assertEquals(ClickSequence.TWO, decision.getClickSequence());
		assertTrue(decision.shouldClick());
		assertTrue(decision.hasSpecificAlert());
		assertEquals(AlertCategory.TRADE_REQUEST, decision.getSpecificAlert());
	}

	private static ClickerPhraseRules phraseRules(String phrase, ClickSequence sequence)
	{
		return ClickerPhraseRules.empty().withAdded(
			new ClickerPhraseRule(
				true,
				sequence,
				ClickerPhraseMatchMode.CONTAINS,
				phrase
			)
		);
	}
}
