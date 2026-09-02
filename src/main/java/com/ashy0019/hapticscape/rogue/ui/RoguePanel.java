package com.ashy0019.hapticscape.rogue.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.rogue.RogueFeedbackEvent;
import com.ashy0019.hapticscape.rogue.blackjack.BlackjackEngine;
import com.ashy0019.hapticscape.rogue.blackjack.BlackjackResult;
import com.ashy0019.hapticscape.rogue.blackjack.BlackjackState;
import com.ashy0019.hapticscape.rogue.blackjack.Card;
import java.awt.BorderLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;

/**
 * Full-canvas Rogue Mode blackjack panel. Controls, chips, table art, future
 * multiplayer seats, and the event log all live inside CasinoScenePanel so the
 * entire RuneLite sidebar becomes the game rather than framing a tiny scene.
 */
public final class RoguePanel extends JPanel
{
	public static final String ROGUE_COINS_KEY = "rogueCoins";
	private static final long REFILL_COINS = BlackjackEngine.DEFAULT_STARTING_COINS;
	private static final long MINIMUM_BET = 10L;
	private static final int MAX_EVENT_LINES = 12;

	private final ConfigManager configManager;
	private final Consumer<RogueFeedbackEvent> feedbackAction;
	private final Deque<String> eventLog = new ArrayDeque<>();
	private final CasinoScenePanel scene;
	private BlackjackEngine engine;
	private BlackjackState state;

	public RoguePanel(ConfigManager configManager, Consumer<RogueFeedbackEvent> feedbackAction)
	{
		this.configManager = configManager;
		this.feedbackAction = feedbackAction;
		engine = new BlackjackEngine(loadCoins());
		state = engine.snapshot();

		scene = new CasinoScenePanel(state, new CasinoScenePanel.ActionHandler()
		{
			@Override
			public void deal(long wager)
			{
				RoguePanel.this.deal(wager);
			}

			@Override
			public void hit()
			{
				RoguePanel.this.hit();
			}

			@Override
			public void stand()
			{
				RoguePanel.this.stand();
			}

			@Override
			public void doubleDown()
			{
				RoguePanel.this.doubleDown();
			}

			@Override
			public void refill()
			{
				RoguePanel.this.refill();
			}
		});

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		add(scene, BorderLayout.CENTER);

		addEvent("Welcome to the Rogue's Den.");
		addEvent("Four seats are reserved for future bad decisions.");
		addEvent("Choose a chip and place your bet.");
		updateUi();
	}

	public void reveal()
	{
		scene.beginReveal();
	}

	private void deal(long wager)
	{
		try
		{
			state = engine.deal(wager);
			addEvent("You wager " + wager + " Rogue Coins.");
			addEvent("The dealer sends four cards across the felt.");
			feedback(RogueFeedbackEvent.DEAL);
			feedbackResult(state.getResult());
			addResultMessageIfNeeded();
			updateUi();
		}
		catch (IllegalArgumentException | IllegalStateException error)
		{
			addEvent(error.getMessage());
			updateUi();
		}
	}

	private void hit()
	{
		try
		{
			int previousCount = state.getPlayerCards().size();
			state = engine.hit();
			if (state.getPlayerCards().size() > previousCount)
			{
				Card card = state.getPlayerCards().get(state.getPlayerCards().size() - 1);
				addEvent("You draw " + cardLabel(card) + ".");
			}
			feedback(RogueFeedbackEvent.HIT);
			feedbackResult(state.getResult());
			addResultMessageIfNeeded();
			updateUi();
		}
		catch (IllegalStateException error)
		{
			addEvent(error.getMessage());
			updateUi();
		}
	}

	private void stand()
	{
		try
		{
			addEvent("You stand on " + state.getPlayerTotal() + ".");
			state = engine.stand();
			feedback(RogueFeedbackEvent.STAND);
			feedbackResult(state.getResult());
			addResultMessageIfNeeded();
			updateUi();
		}
		catch (IllegalStateException error)
		{
			addEvent(error.getMessage());
			updateUi();
		}
	}

	private void doubleDown()
	{
		try
		{
			long originalBet = state.getBet();
			int previousCount = state.getPlayerCards().size();
			state = engine.doubleDown();
			addEvent("Double down: " + originalBet + " more Rogue Coins.");
			if (state.getPlayerCards().size() > previousCount)
			{
				Card card = state.getPlayerCards().get(state.getPlayerCards().size() - 1);
				addEvent("Your one card is " + cardLabel(card) + ".");
			}
			feedback(RogueFeedbackEvent.DOUBLE);
			feedbackResult(state.getResult());
			addResultMessageIfNeeded();
			updateUi();
		}
		catch (IllegalStateException error)
		{
			addEvent(error.getMessage());
			updateUi();
		}
	}

	private void refill()
	{
		if (state.getCoins() >= MINIMUM_BET)
		{
			return;
		}
		engine = new BlackjackEngine(REFILL_COINS);
		state = engine.snapshot();
		addEvent("The house extends a deeply irresponsible 1,000 coin stake.");
		updateUi();
	}

	private void updateUi()
	{
		scene.setState(state);
		scene.setEventLines(new ArrayList<>(eventLog));
		persistCoins(state.getCoins());
	}

	private void addResultMessageIfNeeded()
	{
		if (state.getResult() != BlackjackResult.NONE)
		{
			addEvent(state.getMessage());
		}
	}

	private void addEvent(String message)
	{
		if (message == null || message.trim().isEmpty())
		{
			return;
		}
		eventLog.addLast(message.trim());
		while (eventLog.size() > MAX_EVENT_LINES)
		{
			eventLog.removeFirst();
		}
	}

	private void feedback(RogueFeedbackEvent event)
	{
		if (feedbackAction != null)
		{
			feedbackAction.accept(event);
		}
	}

	private void feedbackResult(BlackjackResult result)
	{
		switch (result)
		{
			case PLAYER_BLACKJACK:
				feedback(RogueFeedbackEvent.BLACKJACK);
				break;
			case PLAYER_WIN:
				feedback(RogueFeedbackEvent.WIN);
				break;
			case DEALER_WIN:
				feedback(RogueFeedbackEvent.LOSS);
				break;
			case PLAYER_BUST:
				feedback(RogueFeedbackEvent.BUST);
				break;
			case PUSH:
				feedback(RogueFeedbackEvent.PUSH);
				break;
			case NONE:
			default:
				break;
		}
	}

	private long loadCoins()
	{
		String value = configManager.getConfiguration(HapticScapeConfig.GROUP, ROGUE_COINS_KEY);
		if (value == null || value.trim().isEmpty())
		{
			return REFILL_COINS;
		}
		try
		{
			return Math.max(0, Long.parseLong(value));
		}
		catch (NumberFormatException ignored)
		{
			return REFILL_COINS;
		}
	}

	private void persistCoins(long coins)
	{
		configManager.setConfiguration(HapticScapeConfig.GROUP, ROGUE_COINS_KEY, coins);
	}

	private static String cardLabel(Card card)
	{
		return card.getRank().getDisplay() + card.getSuit().getSymbol();
	}

	public void close()
	{
		scene.close();
	}
}
