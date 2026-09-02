package com.ashy0019.hapticscape.rogue.blackjack;

import java.util.Objects;

public final class Card
{
	private final Rank rank;
	private final Suit suit;

	public Card(Rank rank, Suit suit)
	{
		this.rank = Objects.requireNonNull(rank, "rank");
		this.suit = Objects.requireNonNull(suit, "suit");
	}

	public Rank getRank()
	{
		return rank;
	}

	public Suit getSuit()
	{
		return suit;
	}

	@Override
	public String toString()
	{
		return rank.getDisplay() + suit.getSymbol();
	}
}
