package com.ashy0019.hapticscape.rogue.blackjack;

public enum Rank
{
	TWO("2", 2),
	THREE("3", 3),
	FOUR("4", 4),
	FIVE("5", 5),
	SIX("6", 6),
	SEVEN("7", 7),
	EIGHT("8", 8),
	NINE("9", 9),
	TEN("10", 10),
	JACK("J", 10),
	QUEEN("Q", 10),
	KING("K", 10),
	ACE("A", 11);

	private final String display;
	private final int blackjackValue;

	Rank(String display, int blackjackValue)
	{
		this.display = display;
		this.blackjackValue = blackjackValue;
	}

	public String getDisplay()
	{
		return display;
	}

	public int getBlackjackValue()
	{
		return blackjackValue;
	}
}
