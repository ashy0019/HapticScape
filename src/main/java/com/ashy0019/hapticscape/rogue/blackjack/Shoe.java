package com.ashy0019.hapticscape.rogue.blackjack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class Shoe
{
	private final Deque<Card> cards;

	private Shoe(Deque<Card> cards)
	{
		this.cards = cards;
	}

	public static Shoe shuffled(int deckCount, Random random)
	{
		if (deckCount < 1)
		{
			throw new IllegalArgumentException("deckCount must be positive");
		}
		Objects.requireNonNull(random, "random");

		List<Card> cards = new ArrayList<>(52 * deckCount);
		for (int deck = 0; deck < deckCount; deck++)
		{
			for (Suit suit : Suit.values())
			{
				for (Rank rank : Rank.values())
				{
					cards.add(new Card(rank, suit));
				}
			}
		}
		Collections.shuffle(cards, random);
		return new Shoe(new ArrayDeque<>(cards));
	}

	/**
	 * Test/helper shoe where the first argument is the first card drawn.
	 */
	public static Shoe stacked(Card... drawOrder)
	{
		Deque<Card> cards = new ArrayDeque<>();
		for (Card card : drawOrder)
		{
			cards.addLast(Objects.requireNonNull(card, "card"));
		}
		return new Shoe(cards);
	}

	int remaining()
	{
		return cards.size();
	}

	Card draw()
	{
		Card card = cards.pollFirst();
		if (card == null)
		{
			throw new IllegalStateException("The blackjack shoe is empty");
		}
		return card;
	}
}
