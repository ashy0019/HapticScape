package com.ashy0019.hapticscape.rogue.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Persistent full-width Rogue Mode launcher. It deliberately lives outside the
 * ordinary HapticScape tabs so unlocking Rogue Mode never creates a seventh,
 * clipped tab. The label uses a RuneScape-chat-inspired rainbow + wave effect.
 */
public final class RogueLauncherPanel extends JComponent
{
	private static final int EXPANDED_HEIGHT = 52;
	private static final long EMERGE_NANOS = 850_000_000L;
	private static final long CELEBRATION_NANOS = 4_500_000_000L;
	private static final Color PANEL_TOP = new Color(47, 35, 26);
	private static final Color PANEL_BOTTOM = new Color(24, 19, 16);
	private static final Color GOLD = new Color(220, 174, 64);
	private static final Color MUTED_GOLD = new Color(174, 137, 58);

	private final Runnable clickAction;
	private final Timer timer;
	private boolean unlocked;
	private boolean active;
	private long emergeStartedNanos = Long.MIN_VALUE;
	private long celebrationStartedNanos = Long.MIN_VALUE;

	public RogueLauncherPanel(Runnable clickAction)
	{
		this.clickAction = clickAction;
		setOpaque(false);
		setVisible(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Enter Rogue Mode");
		setPreferredSize(new Dimension(10, 0));
		setMinimumSize(new Dimension(10, 0));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, EXPANDED_HEIGHT));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (unlocked && event.getButton() == MouseEvent.BUTTON1 && RogueLauncherPanel.this.clickAction != null)
				{
					RogueLauncherPanel.this.clickAction.run();
				}
			}
		});

		timer = new Timer(33, event ->
		{
			updateEmergence();
			repaint();
		});
		timer.setCoalesce(true);
	}

	public void showUnlocked(boolean animate)
	{
		unlocked = true;
		setVisible(true);
		if (animate)
		{
			emergeStartedNanos = System.nanoTime();
			celebrationStartedNanos = emergeStartedNanos;
			setLauncherHeight(1);
		}
		else
		{
			emergeStartedNanos = Long.MIN_VALUE;
			setLauncherHeight(EXPANDED_HEIGHT);
		}
		syncTimerState();
		revalidate();
		repaint();
	}

	public void resetLocked()
	{
		timer.stop();
		unlocked = false;
		active = false;
		emergeStartedNanos = Long.MIN_VALUE;
		celebrationStartedNanos = Long.MIN_VALUE;
		setVisible(false);
		setLauncherHeight(0);
		setToolTipText("Enter Rogue Mode");
		revalidate();
		repaint();
	}

	public void celebrate()
	{
		if (!unlocked)
		{
			showUnlocked(true);
			return;
		}
		celebrationStartedNanos = System.nanoTime();
		syncTimerState();
		repaint();
	}

	public void setActive(boolean active)
	{
		this.active = active;
		if (!active)
		{
			// The normal HapticScape view intentionally uses a stationary gold label.
			celebrationStartedNanos = Long.MIN_VALUE;
		}
		setToolTipText(active ? "Return to HapticScape" : "Enter Rogue Mode");
		syncTimerState();
		repaint();
	}

	private void syncTimerState()
	{
		boolean needsAnimation = active || emergeStartedNanos != Long.MIN_VALUE;
		if (needsAnimation)
		{
			if (!timer.isRunning())
			{
				timer.start();
			}
		}
		else if (timer.isRunning())
		{
			timer.stop();
		}
	}

	private void updateEmergence()
	{
		if (emergeStartedNanos == Long.MIN_VALUE)
		{
			return;
		}

		long elapsed = System.nanoTime() - emergeStartedNanos;
		double progress = Math.min(1.0, elapsed / (double) EMERGE_NANOS);
		// Smoothstep keeps the strip from looking like a plain sliding Swing panel.
		double eased = progress * progress * (3.0 - 2.0 * progress);
		setLauncherHeight(Math.max(1, (int) Math.round(EXPANDED_HEIGHT * eased)));
		revalidate();
		if (progress >= 1.0)
		{
			emergeStartedNanos = Long.MIN_VALUE;
			syncTimerState();
		}
	}

	private void setLauncherHeight(int height)
	{
		int h = Math.max(0, Math.min(EXPANDED_HEIGHT, height));
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		if (!unlocked || getWidth() <= 0 || getHeight() <= 2)
		{
			return;
		}

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int width = getWidth();
			int height = getHeight();
			g.setColor(PANEL_BOTTOM);
			g.fillRect(0, 0, width, height);
			g.setColor(PANEL_TOP);
			g.fillRect(3, 3, Math.max(0, width - 6), Math.max(0, height / 2));
			g.setColor(active ? new Color(255, 208, 75) : GOLD);
			g.drawRect(1, 1, Math.max(0, width - 3), Math.max(0, height - 3));
			g.setColor(MUTED_GOLD);
			g.drawRect(3, 3, Math.max(0, width - 7), Math.max(0, height - 7));

			// Small RuneScape-ish corner notches.
			g.drawLine(1, 9, 9, 1);
			g.drawLine(width - 10, 1, width - 2, 9);
			g.drawLine(1, height - 10, 9, height - 2);
			g.drawLine(width - 10, height - 2, width - 2, height - 10);

			if (active)
			{
				double seconds = System.nanoTime() / 1_000_000_000.0;
				boolean celebrating = celebrationStartedNanos != Long.MIN_VALUE
					&& System.nanoTime() - celebrationStartedNanos < CELEBRATION_NANOS;
				double amplitude = celebrating ? 5.5 : 2.5;
				double speed = celebrating ? 9.0 : 4.8;
				double hueSpeed = celebrating ? 0.75 : 0.30;

				drawRainbowWave(g, "ROGUE", Math.max(18, height / 2 + 4), seconds,
					amplitude, speed, hueSpeed);
			}
			else
			{
				drawGoldLabel(g, "ROGUE", Math.max(18, height / 2 + 4));
			}

			if (height >= 40)
			{
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
				g.setColor(new Color(232, 217, 178));
				String subtitle = active ? "CLICK TO RETURN" : "ENTER THE ROGUE'S DEN";
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(subtitle, Math.max(4, (width - metrics.stringWidth(subtitle)) / 2), height - 8);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	private void drawGoldLabel(Graphics2D g, String text, int baseY)
	{
		int fontSize = Math.max(16, Math.min(24, getHeight() - 20));
		g.setFont(new Font(Font.SERIF, Font.BOLD, fontSize));
		FontMetrics metrics = g.getFontMetrics();
		int x = Math.max(3, (getWidth() - metrics.stringWidth(text)) / 2);

		g.setColor(new Color(0, 0, 0, 220));
		g.drawString(text, x + 2, baseY + 2);
		g.setColor(new Color(255, 205, 74));
		g.drawString(text, x, baseY);
	}

	private void drawRainbowWave(
		Graphics2D g,
		String text,
		int baseY,
		double seconds,
		double amplitude,
		double waveSpeed,
		double hueSpeed)
	{
		int fontSize = Math.max(16, Math.min(24, getHeight() - 20));
		g.setFont(new Font(Font.SERIF, Font.BOLD, fontSize));
		FontMetrics metrics = g.getFontMetrics();
		int spacing = 3;
		int totalWidth = metrics.stringWidth(text) + spacing * Math.max(0, text.length() - 1);
		int x = Math.max(3, (getWidth() - totalWidth) / 2);

		for (int index = 0; index < text.length(); index++)
		{
			String letter = String.valueOf(text.charAt(index));
			int y = baseY + (int) Math.round(Math.sin(seconds * waveSpeed + index * 0.95) * amplitude);
			float hue = (float) ((seconds * hueSpeed + index * 0.16) % 1.0);
			g.setColor(new Color(0, 0, 0, 220));
			g.drawString(letter, x + 2, y + 2);
			g.setColor(Color.getHSBColor(hue, 0.95f, 1.0f));
			g.drawString(letter, x, y);
			x += metrics.stringWidth(letter) + spacing;
		}
	}

	public void close()
	{
		timer.stop();
	}
}
