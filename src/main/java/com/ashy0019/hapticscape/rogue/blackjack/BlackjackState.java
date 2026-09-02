package com.ashy0019.hapticscape.rogue.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable view of one blackjack table state. */
public final class BlackjackState
{
	private final long coins;
	private final long bet;
	private final List<Card> playerCards;
	private final List<Card> dealerCards;
	private final int playerTotal;
	private final int dealerTotal;
	private final boolean dealerHoleHidden;
	private final BlackjackPhase phase;
	private final BlackjackResult result;
	private final String message;
	private final boolean canDouble;

	BlackjackState(
		long coins,
		long bet,
		List<Card> playerCards,
		List<Card> dealerCards,
		int playerTotal,
		int dealerTotal,
		boolean dealerHoleHidden,
		BlackjackPhase phase,
		BlackjackResult result,
		String message,
		boolean canDouble)
	{
		this.coins = coins;
		this.bet = bet;
		this.playerCards = Collections.unmodifiableList(new ArrayList<>(playerCards));
		this.dealerCards = Collections.unmodifiableList(new ArrayList<>(dealerCards));
		this.playerTotal = playerTotal;
		this.dealerTotal = dealerTotal;
		this.dealerHoleHidden = dealerHoleHidden;
		this.phase = phase;
		this.result = result;
		this.message = message;
		this.canDouble = canDouble;
	}

	public long getCoins() { return coins; }
	public long getBet() { return bet; }
	public List<Card> getPlayerCards() { return playerCards; }
	public List<Card> getDealerCards() { return dealerCards; }
	public int getPlayerTotal() { return playerTotal; }
	public int getDealerTotal() { return dealerTotal; }
	public boolean isDealerHoleHidden() { return dealerHoleHidden; }
	public BlackjackPhase getPhase() { return phase; }
	public BlackjackResult getResult() { return result; }
	public String getMessage() { return message; }
	public boolean canHit() { return phase == BlackjackPhase.PLAYER_TURN; }
	public boolean canStand() { return phase == BlackjackPhase.PLAYER_TURN; }
	public boolean canDouble() { return canDouble; }
	public boolean canDeal() { return phase != BlackjackPhase.PLAYER_TURN; }
}
