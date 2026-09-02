package com.ashy0019.hapticscape.rogue.blackjack;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlackjackEngineTest
{
	@Test
	public void aceSoftensToAvoidBust()
	{
		BlackjackHand hand = new BlackjackHand();
		hand.add(card(Rank.ACE));
		hand.add(card(Rank.SIX));
		hand.add(card(Rank.NINE));
		assertEquals(16, hand.getTotal());
		assertFalse(hand.isBust());
	}

	@Test
	public void playerNaturalPaysThreeToTwo()
	{
		BlackjackEngine engine = engine(
			card(Rank.ACE), card(Rank.TEN),
			card(Rank.KING), card(Rank.SEVEN)
		);
		BlackjackState state = engine.deal(20);
		assertEquals(BlackjackResult.PLAYER_BLACKJACK, state.getResult());
		assertEquals(1_030, state.getCoins());
	}

	@Test
	public void dealerNaturalLosesStake()
	{
		BlackjackEngine engine = engine(
			card(Rank.TEN), card(Rank.ACE),
			card(Rank.SEVEN), card(Rank.KING)
		);
		BlackjackState state = engine.deal(20);
		assertEquals(BlackjackResult.DEALER_WIN, state.getResult());
		assertEquals(980, state.getCoins());
	}

	@Test
	public void hitCanBustPlayer()
	{
		BlackjackEngine engine = engine(
			card(Rank.TEN), card(Rank.SEVEN),
			card(Rank.SIX), card(Rank.NINE),
			card(Rank.KING)
		);
		engine.deal(50);
		BlackjackState state = engine.hit();
		assertEquals(BlackjackResult.PLAYER_BUST, state.getResult());
		assertEquals(950, state.getCoins());
	}

	@Test
	public void dealerBustPaysEvenMoney()
	{
		BlackjackEngine engine = engine(
			card(Rank.TEN), card(Rank.TEN),
			card(Rank.EIGHT), card(Rank.SIX),
			card(Rank.KING)
		);
		engine.deal(50);
		BlackjackState state = engine.stand();
		assertEquals(BlackjackResult.PLAYER_WIN, state.getResult());
		assertEquals(1_050, state.getCoins());
	}

	@Test
	public void pushReturnsStake()
	{
		BlackjackEngine engine = engine(
			card(Rank.TEN), card(Rank.NINE),
			card(Rank.SEVEN), card(Rank.EIGHT)
		);
		engine.deal(50);
		BlackjackState state = engine.stand();
		assertEquals(BlackjackResult.PUSH, state.getResult());
		assertEquals(1_000, state.getCoins());
	}

	@Test
	public void doubleDownDoublesStakeAndDrawsOnce()
	{
		BlackjackEngine engine = engine(
			card(Rank.FIVE), card(Rank.SIX),
			card(Rank.SIX), card(Rank.TEN),
			card(Rank.TEN), card(Rank.SIX)
		);
		BlackjackState dealt = engine.deal(40);
		assertTrue(dealt.canDouble());
		BlackjackState state = engine.doubleDown();
		assertEquals(BlackjackResult.PLAYER_WIN, state.getResult());
		assertEquals(80, state.getBet());
		assertEquals(1_080, state.getCoins());
		assertEquals(3, state.getPlayerCards().size());
	}

	private static BlackjackEngine engine(Card... cards)
	{
		return new BlackjackEngine(Shoe.stacked(cards), 1_000);
	}

	private static Card card(Rank rank)
	{
		return new Card(rank, Suit.SPADES);
	}
}
