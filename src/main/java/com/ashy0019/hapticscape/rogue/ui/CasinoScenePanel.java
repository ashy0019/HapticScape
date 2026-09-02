package com.ashy0019.hapticscape.rogue.ui;

import com.ashy0019.hapticscape.rogue.blackjack.BlackjackResult;
import com.ashy0019.hapticscape.rogue.blackjack.BlackjackState;
import com.ashy0019.hapticscape.rogue.blackjack.Card;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
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
	private static final Font NPC_NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);
	private static final Font NPC_STATUS_FONT = new Font(Font.MONOSPACED, Font.BOLD, 9);
	private static final Font NPC_CHAT_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 9);
	private static final Color NPC_CHAT_COLOR = new Color(255, 225, 75);

	// Hand-reduced directly from the RuneScape Classic-era green partyhat
	// reference supplied for the "Buying gf" patron. This 14x14 reduction is
	// deliberately the same width as his hair silhouette, so the crown hugs
	// the head while preserving the uneven RSC spikes.
	private static final String[] GREEN_PARTYHAT_SPRITE = {
		"     G        ",
		"    sG  sg    ",
		"ss  gGs sgs  s",
		"gg  GGG Ggs sg",
		"ggssGGGsGgg gg",
		"ggggGGGGGggsgg",
		"ggggGggggggggg",
		"ggggGGgggggggg",
		"ggggGGgggggggg",
		"sgggGGGGGggggg",
		"sgGGGGGGGggggg",
		" sGGGGGGGggggg",
		"   sGGGGGggss ",
		"      sGss    "
	};

	private final BufferedImage frame = new BufferedImage(
		LOGICAL_WIDTH,
		LOGICAL_HEIGHT,
		BufferedImage.TYPE_INT_ARGB
	);
	private final Timer animationTimer;
	private final ActionHandler actionHandler;
	private final RogueNpcManager npcManager = new RogueNpcManager();
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

		animationTimer = new Timer(33, event ->
		{
			npcManager.update(System.nanoTime());
			repaint();
		});
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
		List<RogueNpcManager.SeatSnapshot> npcSeats = npcManager.snapshot(System.nanoTime());
		drawRoom(g);
		drawHeader(g, npcSeats);
		drawLounge(g);
		drawMainTable(g);
		drawOpenSeats(g, npcSeats);
		drawTableClutter(g);
		drawNpcWagers(g, npcSeats);
		drawHands(g);
		drawNpcSpeech(g, npcSeats);
		drawWagerTray(g);
		drawActionArea(g);
		drawEventLog(g);
		drawReveal(g);
	}

	private static void drawRoom(Graphics2D g)
	{
		// Uneven limewash-and-stone walls framed by heavy oak timbers. Keeping
		// these shapes deterministic avoids visual shimmer while the scene repaints.
		g.setColor(new Color(42, 35, 29));
		g.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);

		g.setColor(new Color(91, 78, 62));
		g.fillRect(0, 0, LOGICAL_WIDTH, 190);
		for (int row = 0, y = 3; y < 190; row++, y += 17)
		{
			int offset = (row & 1) == 0 ? -9 : 4;
			for (int x = offset; x < LOGICAL_WIDTH; x += 31)
			{
				int width = 28 + ((x + row * 7) & 3);
				g.setColor(new Color(106 + (row % 3) * 4, 91 + (row % 2) * 4, 72));
				g.fillRect(x, y, width, 14);
				g.setColor(new Color(62, 53, 45));
				g.drawLine(x, y + 14, x + width, y + 14);
				g.drawLine(x + width, y + 2, x + width, y + 13);
			}
		}

		// Rough plaster patches keep the wall from reading as modern brickwork.
		g.setColor(new Color(122, 105, 79));
		g.fillRect(13, 95, 53, 35);
		g.fillRect(173, 103, 50, 29);
		g.setColor(new Color(83, 69, 55));
		g.drawLine(20, 111, 29, 104);
		g.drawLine(28, 104, 34, 112);
		g.drawLine(184, 119, 192, 111);
		g.drawLine(192, 111, 200, 117);

		// Heavy timber frame and crooked braces.
		Color timber = new Color(57, 34, 22);
		Color timberEdge = new Color(91, 54, 31);
		g.setColor(timber);
		g.fillRect(0, 0, 7, 194);
		g.fillRect(LOGICAL_WIDTH - 7, 0, 7, 194);
		g.fillRect(0, 86, LOGICAL_WIDTH, 8);
		g.fillRect(0, 168, LOGICAL_WIDTH, 10);
		g.fillRect(82, 88, 7, 84);
		g.fillRect(157, 88, 7, 84);
		g.setStroke(new BasicStroke(5f));
		g.drawLine(7, 94, 81, 166);
		g.drawLine(233, 94, 165, 166);
		g.setStroke(new BasicStroke(1f));
		g.setColor(timberEdge);
		g.drawLine(2, 89, 237, 89);
		g.drawLine(2, 171, 237, 171);

		// Dark plank floor below the wall. Most of it is covered by the table, but
		// the visible strips now feel like a tavern rather than tiled masonry.
		g.setColor(new Color(67, 40, 25));
		g.fillRect(0, 178, LOGICAL_WIDTH, LOGICAL_HEIGHT - 178);
		for (int y = 180; y < LOGICAL_HEIGHT; y += 17)
		{
			g.setColor(new Color(91, 52, 30));
			g.drawLine(0, y, LOGICAL_WIDTH, y);
			int offset = ((y / 17) & 1) == 0 ? 8 : 27;
			for (int x = offset; x < LOGICAL_WIDTH; x += 44)
			{
				g.drawLine(x, y, x, Math.min(LOGICAL_HEIGHT, y + 17));
			}
		}
	}

	private void drawHeader(Graphics2D g, List<RogueNpcManager.SeatSnapshot> npcSeats)
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
		int occupied = 0;
		for (RogueNpcManager.SeatSnapshot seat : npcSeats)
		{
			if (seat.getPresence() >= 0.65f)
			{
				occupied++;
			}
		}
		int open = Math.max(0, 4 - occupied);
		String subtitle = open == 0
			? "SOLO RULES  //  TABLE FULL"
			: "SOLO RULES  //  " + open + " OPEN SEAT" + (open == 1 ? "" : "S");
		drawCenteredShadowed(g, subtitle, 51);

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
		// Crooked back-bar with mismatched bottles, mugs and a candle.
		g.setColor(new Color(46, 28, 20));
		g.fillRect(9, 101, 64, 50);
		g.setColor(new Color(113, 67, 36));
		g.fillRect(6, 149, 72, 8);
		g.fillRect(12, 120, 58, 4);
		Color[] bottleColors = {
			new Color(47, 104, 66),
			new Color(126, 58, 43),
			new Color(65, 82, 132),
			new Color(113, 96, 36)
		};
		for (int i = 0; i < bottleColors.length; i++)
		{
			int bx = 15 + i * 13;
			int by = 126 + (i % 2) * 3;
			g.setColor(bottleColors[i]);
			g.fillRect(bx, by, 6, 19 - (i % 2) * 3);
			g.fillRect(bx + 2, by - 5, 2, 6);
			g.setColor(new Color(198, 158, 83));
			g.drawLine(bx + 1, by + 2, bx + 1, by + 8);
		}
		drawTankard(g, 61, 136, 1);

		// Candle sconce on the right wall. The old round shield used to sit
		// directly behind the "Buying gf" patron's head, making his hat look
		// visually detached from him, so that background shape is intentionally
		// left out here.
		g.setColor(new Color(69, 42, 24));
		g.fillRect(203, 106, 4, 30);
		g.setColor(new Color(209, 170, 74));
		g.fillRect(176, 126, 3, 15);
		g.setColor(new Color(255, 210, 86));
		g.fillRect(175, 121, 5, 6);
		g.setColor(new Color(255, 235, 143));
		g.fillRect(176, 119, 3, 4);

		drawPatron(g, 35, 176, new Color(131, 48, 73), 0.0, false);
		drawPatron(g, 211, 176, new Color(67, 64, 137), 2.2, true);
		drawDealer(g, 120, 181);

		long phase = (System.nanoTime() / 3_800_000_000L) % 5;
		if (phase == 0)
		{
			g.setFont(NPC_CHAT_FONT);
			g.setColor(NPC_CHAT_COLOR);
			g.drawString("Buying gf", 170, 116);
		}
	}

	private static void drawPatron(
		Graphics2D g,
		int x,
		int y,
		Color outfit,
		double phase,
		boolean greenPartyHat)
	{
		int bob = (int) Math.round(Math.sin(System.nanoTime() / 700_000_000.0 + phase));
		int hairTopY = y - 39 + bob;

		// Draw the back/crown of the partyhat before the patron himself. The
		// lower edge is then naturally occluded by his hair instead of sitting
		// on top of the portrait like a pasted sprite.
		if (greenPartyHat)
		{
			drawGreenPartyHatBack(g, x, hairTopY);
		}

		g.setColor(new Color(204, 157, 120));
		g.fillRect(x - 6, y - 34 + bob, 12, 12);
		g.setColor(new Color(60, 34, 29));
		if (greenPartyHat)
		{
			// Round the hair silhouette very slightly under the hat. The ordinary
			// patrons keep the original square RSC-style block, but a one-pixel
			// taper here gives the partyhat something skull-shaped to wrap around.
			g.fillRect(x - 6, hairTopY, 12, 1);
			g.fillRect(x - 7, hairTopY + 1, 14, 5);
			g.fillRect(x - 6, hairTopY + 6, 12, 1);
		}
		else
		{
			g.fillRect(x - 7, hairTopY, 14, 7);
		}

		if (greenPartyHat)
		{
			drawGreenPartyHatFrontBand(g, x, hairTopY);
		}

		g.setColor(outfit);
		g.fillRect(x - 10, y - 21 + bob, 20, 27);
		g.setColor(new Color(33, 28, 26));
		g.fillRect(x - 9, y + 6 + bob, 7, 13);
		g.fillRect(x + 2, y + 6 + bob, 7, 13);
	}

	private static void drawGreenPartyHatBack(Graphics2D g, int centerX, int hairTopY)
	{
		Color shadow = new Color(0, 118, 15);
		Color green = new Color(18, 218, 29);
		Color highlight = new Color(58, 239, 64);
		// Keep one fixed horizontal anchor for every row. Re-centering each row
		// independently rounded the original RSC spikes into a green blob. The
		// hat is only 17 px wide against a 14 px head, so a fixed 1-2 px
		// overhang is exactly what makes the paper crown feel fitted rather than
		// pasted on. Its lower rows disappear behind the patron's hair.
		int startX = centerX - 7;
		int startY = hairTopY - 9;

		for (int row = 0; row < GREEN_PARTYHAT_SPRITE.length; row++)
		{
			String line = GREEN_PARTYHAT_SPRITE[row];

			for (int column = 0; column < line.length(); column++)
			{
				char pixel = line.charAt(column);
				switch (pixel)
				{
					case 's':
						g.setColor(shadow);
						break;
					case 'g':
						g.setColor(green);
						break;
					case 'G':
						g.setColor(highlight);
						break;
					default:
						continue;
				}
				g.fillRect(startX + column, startY + row, 1, 1);
			}
		}
	}

	private static void drawGreenPartyHatFrontBand(Graphics2D g, int centerX, int hairTopY)
	{
		// Foreground rim: this is deliberately head-shaped rather than sprite-
		// shaped. The crown sits behind the hair while this shallow band hooks
		// over the forehead and drops one pixel at each temple, so the hat reads
		// as wrapped around the skull.
		Color deepShadow = new Color(0, 74, 10);
		Color shadow = new Color(0, 118, 15);
		Color green = new Color(18, 218, 29);
		Color highlight = new Color(58, 239, 64);

		// Dark underside, exactly the width of the 14 px hair silhouette.
		g.setColor(deepShadow);
		g.fillRect(centerX - 7, hairTopY + 1, 14, 2);

		// Main paper band. The second row is narrower so it curves around the
		// forehead instead of looking like a flat horizontal sticker.
		g.setColor(shadow);
		g.fillRect(centerX - 7, hairTopY, 14, 1);
		g.fillRect(centerX - 6, hairTopY + 1, 12, 1);

		g.setColor(green);
		g.fillRect(centerX - 6, hairTopY, 12, 1);
		g.fillRect(centerX - 5, hairTopY + 1, 10, 1);

		// Temple hooks make the sides visibly wrap behind the head.
		g.setColor(shadow);
		g.fillRect(centerX - 7, hairTopY + 1, 1, 3);
		g.fillRect(centerX + 6, hairTopY + 1, 1, 3);
		g.setColor(deepShadow);
		g.fillRect(centerX - 7, hairTopY + 3, 1, 1);
		g.fillRect(centerX + 6, hairTopY + 3, 1, 1);

		// Small top/front highlight retains the folded-paper look.
		g.setColor(highlight);
		g.fillRect(centerX - 3, hairTopY, 4, 1);
	}

	private static void drawDealer(Graphics2D g, int x, int y)
	{
		// Adult tavern-wench / courtesan silhouette: long auburn hair, cream
		// peasant blouse, wine-red lace bodice and arms resting on the table.
		// The shapes are deliberately chunky so she remains readable at 240 px.
		Color hairDark = new Color(76, 38, 25);
		Color hairLight = new Color(130, 66, 34);
		Color skin = new Color(218, 166, 126);
		Color skinShade = new Color(178, 119, 89);
		Color blouse = new Color(229, 218, 183);
		Color blouseShade = new Color(188, 176, 145);
		Color bodice = new Color(128, 34, 42);
		Color bodiceDark = new Color(70, 25, 29);

		// Hair behind the head and down over both shoulders.
		g.setColor(hairDark);
		g.fillRect(x - 11, y - 57, 22, 28);
		g.fillRect(x - 15, y - 43, 7, 34);
		g.fillRect(x + 8, y - 43, 7, 34);
		g.setColor(hairLight);
		g.fillRect(x - 8, y - 56, 15, 5);
		g.fillRect(x - 13, y - 39, 3, 22);
		g.fillRect(x + 10, y - 39, 3, 20);

		// Face, neck and simple features.
		g.setColor(skin);
		g.fillRect(x - 7, y - 49, 14, 18);
		g.fillRect(x - 4, y - 32, 8, 8);
		g.setColor(new Color(49, 33, 28));
		g.fillRect(x - 4, y - 42, 2, 2);
		g.fillRect(x + 3, y - 42, 2, 2);
		g.setColor(new Color(142, 49, 50));
		g.fillRect(x - 2, y - 35, 5, 2);

		// Puffy sleeves and exposed neckline.
		g.setColor(blouse);
		g.fillRect(x - 20, y - 27, 10, 20);
		g.fillRect(x + 10, y - 27, 10, 20);
		g.fillRect(x - 11, y - 27, 22, 9);
		g.setColor(blouseShade);
		g.drawLine(x - 18, y - 13, x - 11, y - 13);
		g.drawLine(x + 11, y - 13, x + 18, y - 13);
		g.setColor(skin);
		g.fillRect(x - 6, y - 27, 12, 8);
		g.setColor(skinShade);
		g.drawLine(x, y - 23, x, y - 19);

		// Corseted bodice with gold lacing.
		g.setColor(bodice);
		g.fillRect(x - 11, y - 19, 22, 26);
		g.setColor(bodiceDark);
		g.fillRect(x - 2, y - 18, 4, 25);
		g.setColor(new Color(221, 174, 70));
		for (int yy = y - 15; yy <= y; yy += 5)
		{
			g.drawLine(x - 4, yy, x + 4, yy + 3);
			g.drawLine(x + 4, yy, x - 4, yy + 3);
		}

		// Necklace.
		g.setColor(new Color(231, 188, 72));
		g.drawLine(x - 4, y - 28, x, y - 25);
		g.drawLine(x, y - 25, x + 4, y - 28);
		g.fillRect(x, y - 24, 1, 2);

		// Forearms lean onto the near edge of the table.
		g.setColor(skin);
		g.fillRect(x - 23, y - 10, 14, 6);
		g.fillRect(x + 9, y - 10, 14, 6);
		g.setColor(skinShade);
		g.drawLine(x - 23, y - 4, x - 9, y - 4);
		g.drawLine(x + 9, y - 4, x + 23, y - 4);
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

	private static void drawOpenSeats(Graphics2D g, List<RogueNpcManager.SeatSnapshot> npcSeats)
	{
		drawSeat(g, 7, 278, "SEAT 1", npcSeats.get(0));
		drawSeat(g, 187, 278, "SEAT 2", npcSeats.get(1));
		drawSeat(g, 7, 468, "SEAT 3", npcSeats.get(2));
		drawSeat(g, 187, 468, "SEAT 4", npcSeats.get(3));
	}

	private static void drawSeat(
		Graphics2D g,
		int x,
		int y,
		String label,
		RogueNpcManager.SeatSnapshot npc)
	{
		g.setColor(new Color(43, 29, 22));
		g.fillRoundRect(x, y, 46, 70, 8, 8);
		g.setColor(DARK_GOLD);
		g.drawRoundRect(x, y, 45, 69, 8, 8);

		float presence = npc == null ? 0.0f : npc.getPresence();
		if (presence <= 0.01f)
		{
			g.setColor(new Color(39, 43, 39));
			g.fillOval(x + 15, y + 10, 16, 16);
			g.fillRect(x + 11, y + 28, 24, 18);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
			g.setColor(GOLD);
			drawCenteredInShadowed(g, "OPEN", x, 46, y + 57);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
			g.setColor(new Color(226, 214, 183));
			drawCenteredInShadowed(g, label, x, 46, y + 67);
			return;
		}

		boolean rightSide = npc.getSeatIndex() == 1 || npc.getSeatIndex() == 3;
		int direction = rightSide ? 1 : -1;
		int drift = Math.round((1.0f - presence) * 19.0f) * direction;
		int bob = (int) Math.round(Math.sin(System.nanoTime() / 850_000_000.0 + npc.getSeatIndex()) * 0.7);

		// The occupied seat is now primarily a portrait frame. Labels live
		// outside it so the patron can be substantially larger and the text no
		// longer competes with the character art.
		g.setColor(new Color(24, 18, 14));
		g.fillRect(x + 3, y + 3, 40, 63);
		g.setColor(new Color(93, 64, 30));
		g.drawRect(x + 3, y + 3, 39, 62);

		Graphics2D figure = (Graphics2D) g.create();
		try
		{
			Composite originalComposite = figure.getComposite();
			figure.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.08f, presence)));
			figure.translate(drift, bob);
			drawNpcPortrait(figure, x + 23, y + 3, npc, rightSide);
			figure.setComposite(originalComposite);
		}
		finally
		{
			figure.dispose();
		}

		String name = npc.getName() == null ? "ROGUE" : npc.getName();
		String status = npc.getPhase() == RogueNpcManager.Phase.ARRIVING && presence < 0.55f
			? "ARRIVING"
			: (npc.getPhase() == RogueNpcManager.Phase.LEAVING && presence < 0.55f
				? "LEAVING"
				: "BET " + npc.getWager());

		// Large, separate plaques flank the portrait. They can spill six pixels
		// beyond the narrow seat frame while still remaining inside the 240 px
		// scene, giving seven/eight-character names enough room to breathe.
		int plaqueX = x - 6;
		int plaqueWidth = 58;
		drawNpcPlaque(g, plaqueX, y - 12, plaqueWidth, 12, name, NPC_NAME_FONT,
			new Color(255, 221, 101));
		drawNpcPlaque(g, plaqueX, y + 70, plaqueWidth, 12, status, NPC_STATUS_FONT,
			new Color(240, 232, 202));
	}

	private static void drawNpcPlaque(
		Graphics2D g,
		int x,
		int y,
		int width,
		int height,
		String text,
		Font font,
		Color textColor)
	{
		g.setColor(new Color(14, 11, 9, 235));
		g.fillRoundRect(x, y, width, height, 4, 4);
		g.setColor(new Color(116, 82, 30));
		g.drawRoundRect(x, y, width - 1, height - 1, 4, 4);

		Object previousTextHint = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		try
		{
			g.setFont(font);
			g.setColor(textColor);
			FontMetrics metrics = g.getFontMetrics();
			int textX = x + Math.max(2, (width - metrics.stringWidth(text)) / 2);
			int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
			g.setColor(new Color(0, 0, 0, 210));
			g.drawString(text, textX + 1, textY + 1);
			g.setColor(textColor);
			g.drawString(text, textX, textY);
		}
		finally
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, previousTextHint);
		}
	}

	private static void drawNpcPortrait(
		Graphics2D g,
		int centerX,
		int topY,
		RogueNpcManager.SeatSnapshot npc,
		boolean rightSide)
	{
		Color skin = npc.getSkin();
		Color hair = npc.getHair();
		Color outfit = npc.getOutfit();
		Color accent = npc.getAccent();
		int look = npc.getIdleAction() == RogueNpcManager.IdleAction.LOOK_LEFT
			? -1
			: (npc.getIdleAction() == RogueNpcManager.IdleAction.LOOK_RIGHT ? 1 : 0);
		int style = npc.getStyle();

		// Larger chair/portrait proportions. The previous 10 px head and 18 px
		// torso read like an icon; this version fills most of the 46x70 frame and
		// lets hair, hats, eyes and idle motions survive RuneLite scaling.
		g.setColor(new Color(63, 42, 29));
		g.fillRect(centerX - 15, topY + 17, 30, 36);
		g.setColor(new Color(108, 70, 39));
		g.drawRect(centerX - 14, topY + 18, 28, 34);
		g.drawLine(centerX - 12, topY + 21, centerX + 12, topY + 21);

		g.setColor(skin);
		g.fillRect(centerX - 7 + look, topY + 7, 14, 15);
		g.setColor(hair);
		switch (style)
		{
			case 0: // hooded rogue
				g.fillRect(centerX - 9, topY + 2, 18, 9);
				g.fillRect(centerX - 10, topY + 8, 4, 15);
				g.fillRect(centerX + 6, topY + 8, 4, 15);
				break;
			case 1: // mercenary / short cropped hair
				g.fillRect(centerX - 8, topY + 3, 16, 6);
				g.setColor(accent);
				g.fillRect(centerX - 11, topY + 23, 5, 7);
				g.fillRect(centerX + 6, topY + 23, 5, 7);
				break;
			case 2: // merchant cap
				g.fillRect(centerX - 8, topY + 4, 16, 5);
				g.setColor(accent);
				g.fillRect(centerX - 10, topY + 1, 19, 5);
				g.fillRect(centerX + 7, topY + 4, 6, 2);
				break;
			case 3: // miner / scruffy beard
				g.fillRect(centerX - 8, topY + 3, 16, 6);
				g.fillRect(centerX - 5, topY + 18, 10, 7);
				g.setColor(accent);
				g.fillRect(centerX - 9, topY + 1, 18, 3);
				break;
			case 4: // little noble hat and feather
				g.fillRect(centerX - 8, topY + 4, 16, 5);
				g.setColor(accent);
				g.fillRect(centerX - 10, topY + 1, 19, 5);
				g.drawLine(centerX + 6, topY + 1, centerX + 10, topY - 5);
				g.drawLine(centerX + 10, topY - 5, centerX + 11, topY + 1);
				break;
			default: // shaggy drifter
				g.fillRect(centerX - 9, topY + 2, 18, 7);
				g.fillRect(centerX - 9, topY + 7, 4, 11);
				break;
		}

		// Two-pixel eyes are intentionally more legible than the former single
		// texels, while still preserving the tiny left/right glance animation.
		g.setColor(new Color(41, 31, 27));
		g.fillRect(centerX - 4 + look, topY + 12, 2, 2);
		g.fillRect(centerX + 3 + look, topY + 12, 2, 2);

		g.setColor(outfit);
		g.fillRect(centerX - 12, topY + 24, 24, 23);
		g.setColor(accent);
		g.fillRect(centerX - 12, topY + 35, 24, 4);
		g.setColor(new Color(38, 31, 28));
		g.fillRect(centerX - 10, topY + 47, 8, 12);
		g.fillRect(centerX + 2, topY + 47, 8, 12);

		float action = npc.getActionProgress();
		int reach = Math.round(action * 8.0f);
		int tableDirection = rightSide ? -1 : 1;
		g.setColor(skin);
		if (npc.getIdleAction() == RogueNpcManager.IdleAction.FIDDLE_CHIPS)
		{
			int handX = centerX + tableDirection * (12 + reach);
			g.drawLine(centerX + tableDirection * 9, topY + 30, handX, topY + 35);
			g.fillRect(handX - 1, topY + 34, 4, 4);
		}
		else if (npc.getIdleAction() == RogueNpcManager.IdleAction.SIP)
		{
			int lift = Math.round(action * 12.0f);
			int mugX = centerX + tableDirection * 11;
			int mugY = topY + 34 - lift;
			g.drawLine(centerX + tableDirection * 8, topY + 29, mugX, mugY + 5);
			drawTinyTankard(g, mugX - (rightSide ? 6 : 0), mugY);
		}
		else
		{
			g.drawLine(centerX - 10, topY + 30, centerX - 14, topY + 39);
			g.drawLine(centerX + 10, topY + 30, centerX + 14, topY + 39);
		}
	}

	private static void drawTinyTankard(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(107, 72, 41));
		g.fillRect(x, y, 5, 6);
		g.setColor(new Color(178, 126, 63));
		g.drawLine(x + 1, y + 1, x + 3, y + 1);
		g.setColor(new Color(107, 72, 41));
		g.drawOval(x + 4, y + 1, 4, 4);
	}

	private static void drawTableClutter(Graphics2D g)
	{
		// Each future seat has its own abandoned little fortune. This makes the
		// table feel inhabited even while the seats are still marked OPEN.
		drawChipPile(g, 57, 307, new Color(158, 45, 43), 4, false);
		drawChipPile(g, 65, 315, new Color(52, 77, 143), 3, true);
		drawLooseChip(g, 72, 326, new Color(210, 174, 62));
		drawTankard(g, 57, 337, 0);

		drawChipPile(g, 176, 307, new Color(47, 118, 70), 5, true);
		drawChipPile(g, 169, 318, new Color(115, 54, 133), 2, false);
		drawLooseChip(g, 163, 329, new Color(166, 48, 48));
		drawDice(g, 174, 340, 1, 5);

		drawChipPile(g, 57, 497, new Color(43, 43, 43), 5, false);
		drawChipPile(g, 65, 507, new Color(158, 45, 43), 2, true);
		drawLooseChip(g, 75, 518, new Color(54, 82, 149));
		drawCoinPurse(g, 57, 530);

		drawChipPile(g, 176, 494, new Color(111, 54, 133), 6, true);
		drawChipPile(g, 168, 508, new Color(48, 122, 73), 3, false);
		drawLooseChip(g, 163, 520, new Color(203, 169, 57));
		drawDagger(g, 163, 537);

		// Dealer-side junk: a discard tray, an old cup and a few loose wagers.
		drawCardShoe(g, 181, 221);
		drawTankard(g, 37, 224, 0);
		drawLooseChip(g, 75, 365, new Color(157, 45, 43));
		drawLooseChip(g, 84, 371, new Color(48, 122, 73));
		drawLooseChip(g, 154, 365, new Color(55, 80, 146));
		drawLooseChip(g, 161, 374, new Color(199, 164, 54));
		drawDice(g, 44, 381, 2, 6);
		drawDice(g, 184, 380, 3, 4);

		// A few dark marks/rings in the felt keep it from looking factory-new.
		g.setColor(new Color(12, 75, 41));
		g.drawOval(82, 346, 15, 6);
		g.drawArc(143, 527, 16, 7, 0, 280);
		g.drawLine(100, 548, 107, 551);
		g.drawLine(109, 551, 114, 549);
	}

	private static void drawNpcSpeech(Graphics2D g, List<RogueNpcManager.SeatSnapshot> npcSeats)
	{
		// Only one patron speaks at a time (enforced by RogueNpcManager), so the
		// den feels conversational rather than like four overlapping tooltips.
		// The styling intentionally matches the yellow "Buying gf" line in the
		// lounge: same 9 px bold sans-serif face and the exact same gold-yellow.
		int[] baselineY = {374, 374, 565, 565};
		for (RogueNpcManager.SeatSnapshot npc : npcSeats)
		{
			String line = npc.getSpeechLine();
			float alpha = Math.min(npc.getPresence(), npc.getSpeechAlpha());
			if (line == null || line.isEmpty() || alpha <= 0.03f)
			{
				continue;
			}

			int index = npc.getSeatIndex();
			boolean rightSide = index == 1 || index == 3;
			Graphics2D speech = (Graphics2D) g.create();
			try
			{
				speech.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.05f, alpha)));
				speech.setFont(NPC_CHAT_FONT);
				FontMetrics metrics = speech.getFontMetrics();
				int width = metrics.stringWidth(line);
				int x = rightSide ? 181 - width : 59;
				int y = baselineY[index];

				// One-pixel dark shadow keeps the RSC-style floating chat readable on
				// green felt without introducing a modern speech bubble.
				speech.setColor(new Color(22, 16, 10));
				speech.drawString(line, x + 1, y + 1);
				speech.setColor(NPC_CHAT_COLOR);
				speech.drawString(line, x, y);
			}
			finally
			{
				speech.dispose();
			}
		}
	}

	private static void drawNpcWagers(Graphics2D g, List<RogueNpcManager.SeatSnapshot> npcSeats)
	{
		int[] wagerX = {80, 160, 80, 160};
		int[] wagerY = {321, 321, 545, 545};
		for (RogueNpcManager.SeatSnapshot npc : npcSeats)
		{
			float presence = npc.getPresence();
			if (presence <= 0.08f)
			{
				continue;
			}

			int index = npc.getSeatIndex();
			boolean rightSide = index == 1 || index == 3;
			int actionShift = npc.getIdleAction() == RogueNpcManager.IdleAction.FIDDLE_CHIPS
				? Math.round(npc.getActionProgress() * (rightSide ? -5.0f : 5.0f))
				: 0;

			Graphics2D chips = (Graphics2D) g.create();
			try
			{
				chips.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.08f, presence)));
				drawChipPile(
					chips,
					wagerX[index] + actionShift,
					wagerY[index],
					npc.getChipColor(),
					Math.max(1, Math.min(7, npc.getChipCount())),
					rightSide
				);
				if (npc.getChipCount() >= 6)
				{
					drawLooseChip(
						chips,
						wagerX[index] + (rightSide ? -10 : 10),
						wagerY[index] + 4,
						npc.getAccent()
					);
				}
			}
			finally
			{
				chips.dispose();
			}
		}
	}

	private static void drawChipPile(Graphics2D g, int x, int y, Color color, int count, boolean leanRight)
	{
		int dx = leanRight ? 1 : 0;
		for (int index = 0; index < count; index++)
		{
			int yy = y - index * 3;
			int xx = x + index * dx;
			g.setColor(new Color(35, 27, 22));
			g.fillOval(xx - 5, yy - 1, 11, 5);
			g.setColor(color);
			g.fillOval(xx - 5, yy - 2, 11, 4);
			g.setColor(new Color(235, 220, 169));
			g.drawLine(xx - 3, yy - 1, xx - 1, yy - 1);
		}
	}

	private static void drawLooseChip(Graphics2D g, int x, int y, Color color)
	{
		g.setColor(new Color(35, 27, 22));
		g.fillOval(x - 4, y - 2, 9, 5);
		g.setColor(color);
		g.fillOval(x - 4, y - 3, 9, 5);
		g.setColor(new Color(232, 213, 158));
		g.drawLine(x - 2, y - 1, x + 2, y - 1);
	}

	private static void drawTankard(Graphics2D g, int x, int y, int tilt)
	{
		g.setColor(new Color(92, 63, 38));
		g.fillRect(x, y, 10, 11);
		g.setColor(new Color(146, 103, 55));
		g.fillRect(x + 2, y + 1, 6, 8);
		g.setColor(new Color(184, 140, 77));
		g.drawLine(x + 2, y + 2, x + 7, y + 2);
		g.setColor(new Color(92, 63, 38));
		g.drawOval(x + 7 + tilt, y + 2, 7, 7);
	}

	private static void drawDice(Graphics2D g, int x, int y, int first, int second)
	{
		drawDie(g, x, y, first);
		drawDie(g, x + 9, y + 4, second);
	}

	private static void drawDie(Graphics2D g, int x, int y, int value)
	{
		g.setColor(new Color(228, 213, 177));
		g.fillRect(x, y, 7, 7);
		g.setColor(new Color(49, 38, 31));
		g.drawRect(x, y, 7, 7);
		int[][] pips = {
			{},
			{3, 3},
			{1, 1, 5, 5},
			{1, 1, 3, 3, 5, 5},
			{1, 1, 5, 1, 1, 5, 5, 5},
			{1, 1, 5, 1, 3, 3, 1, 5, 5, 5},
			{1, 1, 5, 1, 1, 3, 5, 3, 1, 5, 5, 5}
		};
		int safe = Math.max(1, Math.min(6, value));
		for (int i = 0; i < pips[safe].length; i += 2)
		{
			g.fillRect(x + pips[safe][i], y + pips[safe][i + 1], 1, 1);
		}
	}

	private static void drawCoinPurse(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(94, 55, 34));
		g.fillOval(x - 7, y, 15, 11);
		g.setColor(new Color(143, 88, 47));
		g.fillOval(x - 5, y + 1, 11, 8);
		g.setColor(new Color(214, 171, 76));
		g.drawLine(x - 4, y + 1, x + 5, y + 1);
		g.drawLine(x - 2, y - 2, x + 3, y + 2);
	}

	private static void drawDagger(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(193, 190, 175));
		g.drawLine(x, y, x + 17, y - 8);
		g.drawLine(x + 1, y + 1, x + 18, y - 7);
		g.setColor(new Color(82, 53, 31));
		g.setStroke(new BasicStroke(3f));
		g.drawLine(x + 17, y - 7, x + 23, y - 10);
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(209, 165, 64));
		g.drawLine(x + 15, y - 4, x + 19, y - 12);
	}

	private static void drawCardShoe(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(47, 31, 23));
		g.fillRect(x, y, 24, 12);
		g.setColor(new Color(119, 74, 39));
		g.drawRect(x, y, 24, 12);
		g.setColor(new Color(231, 221, 192));
		for (int i = 0; i < 4; i++)
		{
			g.drawLine(x + 3 + i * 4, y + 2, x + 8 + i * 4, y + 2);
		}
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
