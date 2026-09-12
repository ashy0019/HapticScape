package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.Level99CelebrationController;
import com.ashy0019.hapticscape.ui.Level99CelebrationRenderer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Transparent standalone host for HapticScape's neutral Level-99 renderer. */
public final class StandaloneLevel99GlassPane extends JComponent
{
	private final Level99CelebrationRenderer renderer;
	private final Timer repaintTimer;

	public StandaloneLevel99GlassPane(Level99CelebrationController controller)
	{
		this.renderer = new Level99CelebrationRenderer(controller);
		this.repaintTimer = new Timer(33, event -> repaint());
		setOpaque(false);
		setFocusable(false);
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		repaintTimer.start();
	}

	@Override
	public void removeNotify()
	{
		repaintTimer.stop();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Dimension size = Level99CelebrationRenderer.getRenderSize();
		int x = Math.max(0, (getWidth() - size.width) / 2);
		int y = 12;
		Graphics2D copy = (Graphics2D) graphics.create();
		try
		{
			copy.translate(x, y);
			renderer.render(copy);
		}
		finally
		{
			copy.dispose();
		}
	}

	@Override
	public boolean contains(int x, int y)
	{
		return false;
	}
}
