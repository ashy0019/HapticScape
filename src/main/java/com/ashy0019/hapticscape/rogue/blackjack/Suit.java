package com.ashy0019.hapticscape.rogue.blackjack;

public enum Suit
{
	CLUBS("♣"),
	DIAMONDS("♦"),
	HEARTS("♥"),
	SPADES("♠");

	private final String symbol;

	Suit(String symbol)
	{
		this.symbol = symbol;
	}

	public String getSymbol()
	{
		return symbol;
	}

	public boolean isRed()
	{
		return this == DIAMONDS || this == HEARTS;
	}
}
