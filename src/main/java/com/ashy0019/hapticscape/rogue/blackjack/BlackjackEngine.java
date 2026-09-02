package com.ashy0019.hapticscape.rogue.blackjack;

import java.util.Random;

/**
 * UI-free single-hand blackjack engine. Dealer stands on all 17s.
 */
public final class BlackjackEngine
{
	public static final long DEFAULT_STARTING_COINS = 1_000;

	private Shoe shoe;
	private final Random random;
	private final boolean reshuffle;
	private final BlackjackHand player = new BlackjackHand();
	private final BlackjackHand dealer = new BlackjackHand();
	private long coins;
	private long bet;
	private BlackjackPhase phase = BlackjackPhase.READY;
	private BlackjackResult result = BlackjackResult.NONE;
	private String message = "Place a bet.";

	public BlackjackEngine()
	{
		this(DEFAULT_STARTING_COINS);
	}

	public BlackjackEngine(long startingCoins)
	{
		this(new Random(), startingCoins);
	}

	private BlackjackEngine(Random random, long startingCoins)
	{
		if (startingCoins < 0)
		{
			throw new IllegalArgumentException("startingCoins cannot be negative");
		}
		this.random = random;
		this.reshuffle = true;
		this.shoe = Shoe.shuffled(4, random);
		this.coins = startingCoins;
	}

	public BlackjackEngine(Shoe shoe, long startingCoins)
	{
		if (startingCoins < 0)
		{
			throw new IllegalArgumentException("startingCoins cannot be negative");
		}
		this.shoe = shoe;
		this.coins = startingCoins;
		this.random = null;
		this.reshuffle = false;
	}

	public BlackjackState deal(long wager)
	{
		if (phase == BlackjackPhase.PLAYER_TURN)
		{
			throw new IllegalStateException("A hand is already in progress");
		}
		if (wager <= 0 || wager > coins)
		{
			throw new IllegalArgumentException("Bet must be positive and no greater than the bankroll");
		}

		ensurePlayableShoe();
		player.clear();
		dealer.clear();
		result = BlackjackResult.NONE;
		bet = wager;
		coins -= wager;

		player.add(shoe.draw());
		dealer.add(shoe.draw());
		player.add(shoe.draw());
		dealer.add(shoe.draw());

		if (player.isBlackjack() || dealer.isBlackjack())
		{
			settleNaturals();
		}
		else
		{
			phase = BlackjackPhase.PLAYER_TURN;
			message = "Your move.";
		}
		return snapshot();
	}

	public BlackjackState hit()
	{
		requirePlayerTurn();
		player.add(shoe.draw());
		if (player.isBust())
		{
			finish(BlackjackResult.PLAYER_BUST, "Bust. The house sends its regards.", 0);
		}
		else if (player.getTotal() == 21)
		{
			playDealerAndSettle();
		}
		else
		{
			message = "Hit or stand.";
		}
		return snapshot();
	}

	public BlackjackState stand()
	{
		requirePlayerTurn();
		playDealerAndSettle();
		return snapshot();
	}

	public BlackjackState doubleDown()
	{
		requirePlayerTurn();
		if (player.getCards().size() != 2 || coins < bet)
		{
			throw new IllegalStateException("Double down is not available");
		}

		coins -= bet;
		bet *= 2;
		player.add(shoe.draw());
		if (player.isBust())
		{
			finish(BlackjackResult.PLAYER_BUST, "Double down. Double regret.", 0);
		}
		else
		{
			playDealerAndSettle();
		}
		return snapshot();
	}

	public BlackjackState snapshot()
	{
		boolean holeHidden = phase == BlackjackPhase.PLAYER_TURN;
		int shownDealerTotal = holeHidden && !dealer.getCards().isEmpty()
			? dealer.getCards().get(0).getRank().getBlackjackValue()
			: dealer.getTotal();
		boolean canDouble = phase == BlackjackPhase.PLAYER_TURN
			&& player.getCards().size() == 2
			&& coins >= bet;
		return new BlackjackState(
			coins,
			bet,
			player.getCards(),
			dealer.getCards(),
			player.getTotal(),
			shownDealerTotal,
			holeHidden,
			phase,
			result,
			message,
			canDouble
		);
	}

	private void settleNaturals()
	{
		if (player.isBlackjack() && dealer.isBlackjack())
		{
			finish(BlackjackResult.PUSH, "Two naturals. Nobody gets to feel superior.", bet);
		}
		else if (player.isBlackjack())
		{
			// Return stake plus 3:2 winnings. Odd half-coins round down intentionally.
			finish(BlackjackResult.PLAYER_BLACKJACK, "BLACKJACK!", bet * 5 / 2);
		}
		else
		{
			finish(BlackjackResult.DEALER_WIN, "Dealer blackjack.", 0);
		}
	}

	private void playDealerAndSettle()
	{
		while (dealer.getTotal() < 17)
		{
			dealer.add(shoe.draw());
		}

		int playerTotal = player.getTotal();
		int dealerTotal = dealer.getTotal();
		if (dealer.isBust() || playerTotal > dealerTotal)
		{
			finish(BlackjackResult.PLAYER_WIN, "You win.", bet * 2);
		}
		else if (playerTotal == dealerTotal)
		{
			finish(BlackjackResult.PUSH, "Push.", bet);
		}
		else
		{
			finish(BlackjackResult.DEALER_WIN, "Dealer wins.", 0);
		}
	}

	private void finish(BlackjackResult result, String message, long payout)
	{
		coins += payout;
		this.result = result;
		this.message = message;
		phase = BlackjackPhase.ROUND_OVER;
	}

	private void ensurePlayableShoe()
	{
		if (reshuffle && shoe.remaining() < 20)
		{
			shoe = Shoe.shuffled(4, random);
		}
	}

	private void requirePlayerTurn()
	{
		if (phase != BlackjackPhase.PLAYER_TURN)
		{
			throw new IllegalStateException("It is not the player's turn");
		}
	}
}
