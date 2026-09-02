package com.ashy0019.hapticscape.rogue.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlackjackHand
{
	private final List<Card> cards = new ArrayList<>();

	void add(Card card)
	{
		cards.add(card);
	}

	void clear()
	{
		cards.clear();
	}

	public List<Card> getCards()
	{
		return Collections.unmodifiableList(cards);
	}

	public int getTotal()
	{
		int total = 0;
		int aces = 0;
		for (Card card : cards)
		{
			total += card.getRank().getBlackjackValue();
			if (card.getRank() == Rank.ACE)
			{
				aces++;
			}
		}

		while (total > 21 && aces > 0)
		{
			total -= 10;
			aces--;
		}
		return total;
	}

	public boolean isBlackjack()
	{
		return cards.size() == 2 && getTotal() == 21;
	}

	public boolean isBust()
	{
		return getTotal() > 21;
	}
}
