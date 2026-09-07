package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.HapticScapePlugin;
import com.ashy0019.hapticscape.Level99CelebrationController;
import com.ashy0019.hapticscape.ui.Level99CelebrationRenderer;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** RuneLite host wrapper for the source-neutral Level 99 celebration renderer. */
public final class RuneLiteLevel99CelebrationOverlay extends Overlay
{
	private final Level99CelebrationRenderer renderer;

	public RuneLiteLevel99CelebrationOverlay(
		HapticScapePlugin plugin,
		Level99CelebrationController controller)
	{
		super(plugin);
		renderer = new Level99CelebrationRenderer(controller);
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST);
		setMovable(false);
		setSnappable(false);
		setResettable(false);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		return renderer.render(graphics);
	}
}
