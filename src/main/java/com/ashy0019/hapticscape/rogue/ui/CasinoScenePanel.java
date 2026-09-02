package com.ashy0019.hapticscape.rogue.ui;

import com.ashy0019.hapticscape.rogue.blackjack.BlackjackResult;
import com.ashy0019.hapticscape.rogue.blackjack.BlackjackState;
import com.ashy0019.hapticscape.rogue.blackjack.Card;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Full-height Rogue's Den game canvas. The entire RuneLite plugin body is used
 * as one illustrated blackjack surface: room art, future multiplayer/AI seats,
 * table, chips, controls, and an event log all scale together.
 */
public final class CasinoScenePanel extends JPanel
{
	public interface ActionHandler
	{
		void deal(long wager);
		void hit();
		void stand();
		void doubleDown();
		void refill();
	}

	private static final int LOGICAL_WIDTH = 240;
	private static final int LOGICAL_HEIGHT = 940;
	private static final int CARD_WIDTH = 43;
	private static final int CARD_HEIGHT = 59;
	private static final int CARD_STEP = 31;
	private static final long REVEAL_NANOS = 4_500_000_000L;
	private static final long[] BETS = {10L, 25L, 50L, 100L, 250L};

	private static final Rectangle HIT_RECT = new Rectangle(8, 716, 108, 42);
	private static final Rectangle STAND_RECT = new Rectangle(124, 716, 108, 42);
	private static final Rectangle DOUBLE_RECT = new Rectangle(8, 766, 108, 42);
	private static final Rectangle DEAL_RECT = new Rectangle(124, 766, 108, 42);

	private static final Color GOLD = new Color(244, 195, 65);
	private static final Color DARK_GOLD = new Color(145, 105, 34);
	private static final Color CARD_RED = new Color(166, 31, 36);
	private static final Color FELT = new Color(21, 98, 53);
	private static final Color FELT_DARK = new Color(12, 62, 34);
	private static final Color LIGHT_TEXT = new Color(247, 240, 215);
	private static final Color PANEL_DARK = new Color(20, 17, 15);
	private static final Color PANEL_MID = new Color(45, 36, 30);
	private static final Color STONE = new Color(65, 60, 54);
	private static final Color STONE_LINE = new Color(87, 80, 71);

	private final BufferedImage frame = new BufferedImage(
		LOGICAL_WIDTH,
		LOGICAL_HEIGHT,
		BufferedImage.TYPE_INT_ARGB
	);
	private final Timer animationTimer;
	private final ActionHandler actionHandler;
	private BlackjackState state;
	private List<String> eventLines = Collections.emptyList();
	private long selectedBet = BETS[0];
	private long revealStartedNanos = Long.MIN_VALUE;
	private int hoverX = -1;
	private int hoverY = -1;

	public CasinoScenePanel(BlackjackState initialState, ActionHandler actionHandler)
	{
		this.state = initialState;
		this.actionHandler = actionHandler;
		setPreferredSize(new Dimension(240, 940));
		setMinimumSize(new Dimension(180, 520));
		setOpaque(true);
		setBackground(Color.BLACK);

		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent event)
			{
				updateHover(event.getX(), event.getY());
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent event)
			{
				hoverX = -1;
				hoverY = -1;
				setCursor(Cursor.getDefaultCursor());
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (event.getButton() == MouseEvent.BUTTON1)
				{
					handleClick(toLogicalX(event.getX()), toLogicalY(event.getY()));
				}
			}
		});

		animationTimer = new Timer(33, event -> repaint());
		animationTimer.setCoalesce(true);
		animationTimer.start();
	}

	public void setState(BlackjackState state)
	{
		this.state = state;
		ensureSelectedBetMakesSense();
		repaint();
	}

	public void setEventLines(List<String> eventLines)
	{
		this.eventLines = eventLines == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(eventLines));
		repaint();
	}

	public void beginReveal()
	{
		revealStartedNanos = System.nanoTime();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D logical = frame.createGraphics();
		try
		{
			logical.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_OFF);
			logical.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			renderScene(logical);
		}
		finally
		{
			logical.dispose();
		}

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			// Deliberately fill the entire available Rogue body. The logical scene
			// matches RuneLite's narrow aspect ratio closely enough that the tiny
			// amount of non-uniform scaling is preferable to giant black bars.
			g.drawImage(frame, 0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()), null);
		}
		finally
		{
			g.dispose();
		}
	}

	private void renderScene(Graphics2D g)
	{
		drawRoom(g);
		drawHeader(g);
		drawLounge(g);
		drawMainTable(g);
		drawOpenSeats(g);
		drawHands(g);
		drawWagerTray(g);
		drawActionArea(g);
		drawEventLog(g);
		drawReveal(g);
	}

	private static void drawRoom(Graphics2D g)
	{
		g.setColor(new Color(31, 25, 22));
		g.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);

		g.setColor(STONE);
		g.fillRect(0, 0, LOGICAL_WIDTH, 188);
		g.setColor(STONE_LINE);
		for (int y = 4; y < 188; y += 14)
		{
			int offset = ((y / 14) & 1) == 0 ? 0 : 12;
			for (int x = -offset; x < LOGICAL_WIDTH; x += 24)
			{
				g.drawRect(x, y, 23, 13);
			}
		}

		g.setColor(new Color(73, 45, 29));
		g.fillRect(0, 188, LOGICAL_WIDTH, LOGICAL_HEIGHT - 188);
		g.setColor(new Color(95, 58, 35));
		for (int y = 190; y < LOGICAL_HEIGHT; y += 16)
		{
			g.drawLine(0, y, LOGICAL_WIDTH, y);
			int offset = ((y / 16) & 1) == 0 ? 0 : 18;
			for (int x = 6 + offset; x < LOGICAL_WIDTH; x += 36)
			{
				g.drawLine(x, y, x, Math.min(LOGICAL_HEIGHT, y + 16));
			}
		}
	}

	private void drawHeader(Graphics2D g)
	{
		g.setColor(PANEL_DARK);
		g.fillRect(7, 7, 226, 60);
		g.setColor(GOLD);
		g.drawRect(7, 7, 225, 59);
		g.setColor(DARK_GOLD);
		g.drawRect(10, 10, 219, 53);

		g.setFont(new Font(Font.SERIF, Font.BOLD, 19));
		g.setColor(GOLD);
		drawCentered(g, "THE ROGUE'S DEN", 31);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		g.setColor(new Color(240, 229, 197));
		drawCenteredShadowed(g, "SOLO TABLE  //  4 OPEN SEATS", 51);

		BlackjackState current = state;
		long coins = current == null ? 0 : current.getCoins();
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		g.setColor(GOLD);
		drawStringShadowed(g, "COINS " + coins, 12, 83);
		String bet = "BET " + selectedBet;
		FontMetrics metrics = g.getFontMetrics();
		drawStringShadowed(g, bet, LOGICAL_WIDTH - 12 - metrics.stringWidth(bet), 83);
	}

	private static void drawLounge(Graphics2D g)
	{
		// Bar and bottles give the upper area visual weight instead of dead black.
		g.setColor(new Color(46, 28, 20));
		g.fillRect(8, 92, 70, 62);
		g.setColor(new Color(130, 82, 43));
		g.fillRect(5, 151, 76, 9);
		Color[] bottleColors = {
			new Color(52, 111, 72),
			new Color(133, 64, 48),
			new Color(73, 92, 144),
			new Color(123, 108, 40)
		};
		for (int i = 0; i < bottleColors.length; i++)
		{
			g.setColor(bottleColors[i]);
			g.fillRect(15 + i * 14, 108 + (i % 2) * 4, 6, 27 - (i % 2) * 4);
			g.fillRect(17 + i * 14, 103 + (i % 2) * 4, 2, 6);
		}

		drawPatron(g, 35, 172, new Color(146, 52, 81), 0.0);
		drawPatron(g, 205, 172, new Color(76, 72, 153), 2.2);
		drawDealer(g, 120, 176);

		long phase = (System.nanoTime() / 3_800_000_000L) % 5;
		if (phase == 0)
		{
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
			g.setColor(new Color(255, 225, 75));
			g.drawString("Buying gf", 160, 116);
		}
	}

	private static void drawPatron(Graphics2D g, int x, int y, Color outfit, double phase)
	{
		int bob = (int) Math.round(Math.sin(System.nanoTime() / 700_000_000.0 + phase));
		g.setColor(new Color(204, 157, 120));
		g.fillRect(x - 6, y - 34 + bob, 12, 12);
		g.setColor(new Color(60, 34, 29));
		g.fillRect(x - 7, y - 39 + bob, 14, 7);
		g.setColor(outfit);
		g.fillRect(x - 10, y - 21 + bob, 20, 27);
		g.setColor(new Color(33, 28, 26));
		g.fillRect(x - 9, y + 6 + bob, 7, 13);
		g.fillRect(x + 2, y + 6 + bob, 7, 13);
	}

	private static void drawDealer(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(207, 163, 120));
		g.fillRect(x - 7, y - 37, 14, 14);
		g.setColor(new Color(48, 30, 24));
		g.fillRect(x - 9, y - 43, 18, 8);
		g.setColor(new Color(28, 30, 28));
		g.fillRect(x - 15, y - 22, 30, 32);
		g.setColor(new Color(222, 217, 188));
		g.fillRect(x - 4, y - 20, 8, 27);
		g.setColor(new Color(145, 29, 30));
		g.fillRect(x - 1, y - 17, 3, 18);
	}

	private static void drawMainTable(Graphics2D g)
	{
		g.setColor(new Color(54, 31, 19));
		g.fillRoundRect(5, 174, 230, 437, 54, 54);
		g.setColor(FELT_DARK);
		g.fillRoundRect(10, 180, 220, 425, 48, 48);
		g.setColor(FELT);
		g.fillRoundRect(16, 187, 208, 411, 42, 42);
		g.setColor(new Color(202, 155, 52));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(22, 194, 196, 397, 36, 36);
		g.setStroke(new BasicStroke(1f));
		g.setFont(new Font(Font.SERIF, Font.BOLD, 12));
		g.setColor(new Color(246, 207, 92));
		drawCenteredShadowed(g, "BLACKJACK PAYS 3 TO 2", 212);
	}

	private static void drawOpenSeats(Graphics2D g)
	{
		drawSeat(g, 7, 278, "SEAT 1");
		drawSeat(g, 187, 278, "SEAT 2");
		drawSeat(g, 7, 468, "SEAT 3");
		drawSeat(g, 187, 468, "SEAT 4");
	}

	private static void drawSeat(Graphics2D g, int x, int y, String label)
	{
		g.setColor(new Color(43, 29, 22));
		g.fillRoundRect(x, y, 46, 70, 8, 8);
		g.setColor(DARK_GOLD);
		g.drawRoundRect(x, y, 45, 69, 8, 8);
		g.setColor(new Color(39, 43, 39));
		g.fillOval(x + 15, y + 10, 16, 16);
		g.fillRect(x + 11, y + 28, 24, 18);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		g.setColor(GOLD);
		drawCenteredInShadowed(g, "OPEN", x, 46, y + 57);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
		g.setColor(new Color(226, 214, 183));
		drawCenteredInShadowed(g, label, x, 46, y + 67);
	}

	private void drawHands(Graphics2D g)
	{
		BlackjackState current = state;
		if (current == null)
		{
			return;
		}

		g.setColor(LIGHT_TEXT);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		String dealerLabel = current.getDealerCards().isEmpty()
			? "DEALER"
			: "DEALER  " + dealerDisplayTotal(current);
		drawCenteredShadowed(g, dealerLabel, 240);
		drawCardRow(g, current.getDealerCards(), 250, current.isDealerHoleHidden());

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.setColor(new Color(248, 238, 206));
		drawCenteredShadowed(g, current.getMessage(), 392);

		BlackjackResult result = current.getResult();
		if (result != BlackjackResult.NONE)
		{
			drawResultBadge(g, result);
		}

		g.setColor(LIGHT_TEXT);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		String playerLabel = current.getPlayerCards().isEmpty()
			? "YOU"
			: "YOU  " + current.getPlayerTotal();
		drawCenteredShadowed(g, playerLabel, 444);
		drawCardRow(g, current.getPlayerCards(), 456, false);

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		g.setColor(new Color(248, 224, 157));
		drawCenteredShadowed(g, current.getBet() > 0 ? "ON FELT: " + current.getBet() : "YOUR SEAT", 584);
	}

	private static String dealerDisplayTotal(BlackjackState current)
	{
		return current.isDealerHoleHidden() ? "?" : String.valueOf(current.getDealerTotal());
	}

	private static void drawCardRow(Graphics2D g, List<Card> cards, int y, boolean hideSecond)
	{
		if (cards == null || cards.isEmpty())
		{
			return;
		}
		int rowWidth = CARD_WIDTH + CARD_STEP * Math.max(0, cards.size() - 1);
		int startX = Math.max(57, (LOGICAL_WIDTH - rowWidth) / 2);
		for (int index = 0; index < cards.size(); index++)
		{
			int x = startX + index * CARD_STEP;
			if (hideSecond && index == 1)
			{
				drawCardBack(g, x, y);
			}
			else
			{
				drawCard(g, cards.get(index), x, y);
			}
		}
	}

	private static void drawCard(Graphics2D g, Card card, int x, int y)
	{
		g.setColor(new Color(247, 242, 221));
		g.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT);
		g.setColor(new Color(35, 30, 27));
		g.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT);
		g.setColor(card.getSuit().isRed() ? CARD_RED : new Color(28, 27, 25));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(card.getRank().getDisplay(), x + 5, y + 17);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
		g.drawString(card.getSuit().getSymbol(), x + 5, y + 40);
	}

	private static void drawCardBack(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(119, 31, 39));
		g.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT);
		g.setColor(new Color(236, 207, 132));
		g.drawRect(x, y, CARD_WIDTH, CARD_HEIGHT);
		g.drawRect(x + 4, y + 4, CARD_WIDTH - 8, CARD_HEIGHT - 8);
		g.drawLine(x + 5, y + 5, x + CARD_WIDTH - 5, y + CARD_HEIGHT - 5);
		g.drawLine(x + CARD_WIDTH - 5, y + 5, x + 5, y + CARD_HEIGHT - 5);
	}

	private static void drawResultBadge(Graphics2D g, BlackjackResult result)
	{
		String text;
		switch (result)
		{
			case PLAYER_BLACKJACK:
				text = "BLACKJACK!";
				break;
			case PLAYER_WIN:
				text = "YOU WIN";
				break;
			case PLAYER_BUST:
				text = "BUST";
				break;
			case DEALER_WIN:
				text = "DEALER WINS";
				break;
			case PUSH:
				text = "PUSH";
				break;
			default:
				return;
		}

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		FontMetrics metrics = g.getFontMetrics();
		int width = metrics.stringWidth(text) + 20;
		int x = (LOGICAL_WIDTH - width) / 2;
		g.setColor(new Color(15, 12, 11, 235));
		g.fillRect(x, 407, width, 24);
		g.setColor(GOLD);
		g.drawRect(x, 407, width, 24);
		drawCentered(g, text, 424);
	}

	private void drawWagerTray(Graphics2D g)
	{
		g.setColor(PANEL_DARK);
		g.fillRect(5, 620, 230, 87);
		g.setColor(DARK_GOLD);
		g.drawRect(5, 620, 229, 86);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		g.setColor(GOLD);
		drawCenteredShadowed(g, "CHOOSE YOUR CHIP", 639);

		int[] colors = {
			new Color(160, 48, 48).getRGB(),
			new Color(55, 83, 151).getRGB(),
			new Color(48, 126, 76).getRGB(),
			new Color(42, 42, 42).getRGB(),
			new Color(113, 56, 137).getRGB()
		};
		for (int i = 0; i < BETS.length; i++)
		{
			int cx = chipCenterX(i);
			int cy = 672;
			long wager = BETS[i];
			boolean affordable = state != null && state.canDeal() && state.getCoins() >= wager;
			boolean hovered = isChipHovered(i);
			boolean selected = selectedBet == wager;

			g.setColor(new Color(colors[i], true));
			g.fillOval(cx - 21, cy - 21, 42, 42);
			g.setColor(selected ? GOLD : (hovered && affordable ? new Color(255, 238, 170) : new Color(215, 198, 151)));
			g.setStroke(new BasicStroke(selected ? 3f : 2f));
			g.drawOval(cx - 21, cy - 21, 42, 42);
			g.setStroke(new BasicStroke(1f));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, wager >= 100 ? 10 : 12));
			g.setColor(affordable || selected ? Color.WHITE : new Color(154, 149, 136));
			drawCenteredInShadowed(g, String.valueOf(wager), cx - 21, 42, cy + 4);
		}
	}

	private void drawActionArea(Graphics2D g)
	{
		BlackjackState current = state;
		boolean canHit = current != null && current.canHit();
		boolean canStand = current != null && current.canStand();
		boolean canDouble = current != null && current.canDouble();
		boolean canRefill = current != null && current.canDeal() && current.getCoins() < BETS[0];
		boolean canDeal = current != null && current.canDeal() && current.getCoins() >= selectedBet;

		drawButton(g, HIT_RECT, "HIT", canHit, isHovered(HIT_RECT));
		drawButton(g, STAND_RECT, "STAND", canStand, isHovered(STAND_RECT));
		drawButton(g, DOUBLE_RECT, "DOUBLE", canDouble, isHovered(DOUBLE_RECT));
		drawButton(g, DEAL_RECT, canRefill ? "BEG" : "DEAL", canRefill || canDeal, isHovered(DEAL_RECT));

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		g.setColor(new Color(240, 229, 200));
		String hint = canRefill
			? "THE HOUSE HAS NOT LEARNED ITS LESSON"
			: (current != null && current.canDeal() ? "SELECT A CHIP, THEN DEAL" : "PLAY THE HAND");
		drawCenteredShadowed(g, hint, 818);
	}

	private static void drawButton(Graphics2D g, Rectangle rect, String label, boolean enabled, boolean hovered)
	{
		Color fill;
		Color border;
		Color text;
		if (!enabled)
		{
			fill = new Color(35, 32, 30);
			border = new Color(79, 70, 62);
			text = new Color(119, 113, 105);
		}
		else if (hovered)
		{
			fill = new Color(81, 61, 35);
			border = new Color(255, 220, 111);
			text = Color.WHITE;
		}
		else
		{
			fill = PANEL_MID;
			border = GOLD;
			text = new Color(244, 232, 199);
		}
		g.setColor(fill);
		g.fillRect(rect.x, rect.y, rect.width, rect.height);
		g.setColor(border);
		g.drawRect(rect.x, rect.y, rect.width, rect.height);
		g.drawRect(rect.x + 3, rect.y + 3, rect.width - 6, rect.height - 6);
		g.setFont(new Font(Font.SERIF, Font.BOLD, 15));
		g.setColor(text);
		FontMetrics metrics = g.getFontMetrics();
		int x = rect.x + (rect.width - metrics.stringWidth(label)) / 2;
		int y = rect.y + (rect.height + metrics.getAscent() - metrics.getDescent()) / 2;
		g.drawString(label, x, y);
	}

	private void drawEventLog(Graphics2D g)
	{
		g.setColor(new Color(13, 12, 11, 245));
		g.fillRect(5, 826, 230, 107);
		g.setColor(DARK_GOLD);
		g.drawRect(5, 826, 229, 106);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		g.setColor(GOLD);
		drawStringShadowed(g, "TABLE EVENTS", 11, 842);
		g.setColor(new Color(127, 110, 84));
		g.drawLine(10, 848, 229, 848);

		List<String> wrapped = new ArrayList<>();
		for (String line : eventLines)
		{
			wrapped.addAll(wrap(line, 34));
		}
		int maxLines = 6;
		int start = Math.max(0, wrapped.size() - maxLines);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
		int y = 862;
		for (int index = start; index < wrapped.size(); index++)
		{
			g.setColor(index == wrapped.size() - 1
				? new Color(255, 239, 191)
				: new Color(214, 207, 191));
			drawStringShadowed(g, "> " + wrapped.get(index), 10, y);
			y += 12;
		}
	}

	private void drawReveal(Graphics2D g)
	{
		if (revealStartedNanos == Long.MIN_VALUE)
		{
			return;
		}
		long elapsed = System.nanoTime() - revealStartedNanos;
		if (elapsed >= REVEAL_NANOS)
		{
			revealStartedNanos = Long.MIN_VALUE;
			return;
		}

		double seconds = elapsed / 1_000_000_000.0;
		double progress = elapsed / (double) REVEAL_NANOS;
		int alpha = progress < 0.18
			? (int) (242 * (progress / 0.18))
			: (int) (242 * Math.max(0.0, 1.0 - ((progress - 0.72) / 0.28)));
		alpha = Math.max(0, Math.min(242, alpha));
		g.setColor(new Color(0, 0, 0, alpha));
		g.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);
		drawRainbowWave(g, "ROGUE MODE", 376, seconds);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		g.setColor(new Color(241, 232, 202));
		drawCentered(g, "WELCOME TO THE ROGUE'S DEN", 432);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
		g.setColor(new Color(221, 211, 181));
		drawCentered(g, "BLACKJACK & BAD DECISIONS", 451);
	}

	private static void drawRainbowWave(Graphics2D g, String text, int baseY, double seconds)
	{
		g.setFont(new Font(Font.SERIF, Font.BOLD, 25));
		FontMetrics metrics = g.getFontMetrics();
		int spacing = 2;
		int totalWidth = metrics.stringWidth(text) + spacing * Math.max(0, text.length() - 1);
		int x = (LOGICAL_WIDTH - totalWidth) / 2;
		for (int index = 0; index < text.length(); index++)
		{
			String letter = String.valueOf(text.charAt(index));
			int y = baseY + (int) Math.round(Math.sin(seconds * 8.0 + index * 0.82) * 8.0);
			float hue = (float) ((seconds * 0.65 + index * 0.11) % 1.0);
			g.setColor(new Color(0, 0, 0, 220));
			g.drawString(letter, x + 2, y + 2);
			g.setColor(Color.getHSBColor(hue, 0.95f, 1.0f));
			g.drawString(letter, x, y);
			x += metrics.stringWidth(letter) + spacing;
		}
	}

	private void updateHover(int componentX, int componentY)
	{
		hoverX = toLogicalX(componentX);
		hoverY = toLogicalY(componentY);
		setCursor(isClickable(hoverX, hoverY)
			? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
			: Cursor.getDefaultCursor());
		repaint();
	}

	private void handleClick(int x, int y)
	{
		BlackjackState current = state;
		if (current == null || actionHandler == null)
		{
			return;
		}

		for (int i = 0; i < BETS.length; i++)
		{
			if (isInsideChip(x, y, i) && current.canDeal() && current.getCoins() >= BETS[i])
			{
				selectedBet = BETS[i];
				repaint();
				return;
			}
		}

		if (HIT_RECT.contains(x, y) && current.canHit())
		{
			actionHandler.hit();
		}
		else if (STAND_RECT.contains(x, y) && current.canStand())
		{
			actionHandler.stand();
		}
		else if (DOUBLE_RECT.contains(x, y) && current.canDouble())
		{
			actionHandler.doubleDown();
		}
		else if (DEAL_RECT.contains(x, y) && current.canDeal())
		{
			if (current.getCoins() < BETS[0])
			{
				actionHandler.refill();
			}
			else if (selectedBet <= current.getCoins())
			{
				actionHandler.deal(selectedBet);
			}
		}
	}

	private boolean isClickable(int x, int y)
	{
		BlackjackState current = state;
		if (current == null)
		{
			return false;
		}
		for (int i = 0; i < BETS.length; i++)
		{
			if (isInsideChip(x, y, i) && current.canDeal() && current.getCoins() >= BETS[i])
			{
				return true;
			}
		}
		if (HIT_RECT.contains(x, y) && current.canHit())
		{
			return true;
		}
		if (STAND_RECT.contains(x, y) && current.canStand())
		{
			return true;
		}
		if (DOUBLE_RECT.contains(x, y) && current.canDouble())
		{
			return true;
		}
		return DEAL_RECT.contains(x, y)
			&& current.canDeal()
			&& (current.getCoins() < BETS[0] || selectedBet <= current.getCoins());
	}

	private boolean isHovered(Rectangle rectangle)
	{
		return rectangle.contains(hoverX, hoverY);
	}

	private boolean isChipHovered(int index)
	{
		return isInsideChip(hoverX, hoverY, index);
	}

	private static boolean isInsideChip(int x, int y, int index)
	{
		int dx = x - chipCenterX(index);
		int dy = y - 672;
		return dx * dx + dy * dy <= 22 * 22;
	}

	private static int chipCenterX(int index)
	{
		return 28 + index * 46;
	}

	private int toLogicalX(int componentX)
	{
		return getWidth() <= 0 ? -1 : componentX * LOGICAL_WIDTH / getWidth();
	}

	private int toLogicalY(int componentY)
	{
		return getHeight() <= 0 ? -1 : componentY * LOGICAL_HEIGHT / getHeight();
	}

	private void ensureSelectedBetMakesSense()
	{
		BlackjackState current = state;
		if (current == null || !current.canDeal() || current.getCoins() <= 0 || selectedBet <= current.getCoins())
		{
			return;
		}
		long best = BETS[0];
		for (long wager : BETS)
		{
			if (wager <= current.getCoins())
			{
				best = wager;
			}
		}
		selectedBet = best;
	}

	private static List<String> wrap(String text, int maxChars)
	{
		if (text == null || text.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		List<String> lines = new ArrayList<>();
		String remaining = text.trim();
		while (remaining.length() > maxChars)
		{
			int split = remaining.lastIndexOf(' ', maxChars);
			if (split <= 0)
			{
				split = maxChars;
			}
			lines.add(remaining.substring(0, split).trim());
			remaining = remaining.substring(split).trim();
		}
		if (!remaining.isEmpty())
		{
			lines.add(remaining);
		}
		return lines;
	}

	private static void drawStringShadowed(Graphics2D g, String text, int x, int y)
	{
		if (text == null)
		{
			return;
		}
		Color original = g.getColor();
		g.setColor(new Color(0, 0, 0, 210));
		g.drawString(text, x + 1, y + 1);
		g.setColor(original);
		g.drawString(text, x, y);
	}

	private static void drawCenteredShadowed(Graphics2D g, String text, int y)
	{
		if (text == null)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		drawStringShadowed(g, text, (LOGICAL_WIDTH - metrics.stringWidth(text)) / 2, y);
	}

	private static void drawCenteredInShadowed(Graphics2D g, String text, int x, int width, int y)
	{
		FontMetrics metrics = g.getFontMetrics();
		drawStringShadowed(g, text, x + (width - metrics.stringWidth(text)) / 2, y);
	}

	private static void drawCentered(Graphics2D g, String text, int y)
	{
		if (text == null)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(text, (LOGICAL_WIDTH - metrics.stringWidth(text)) / 2, y);
	}

	private static void drawCenteredIn(Graphics2D g, String text, int x, int width, int y)
	{
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, y);
	}

	public void close()
	{
		animationTimer.stop();
	}
}
