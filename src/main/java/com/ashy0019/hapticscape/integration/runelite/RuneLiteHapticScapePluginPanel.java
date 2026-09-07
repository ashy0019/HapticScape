package com.ashy0019.hapticscape.integration.runelite;

import java.awt.BorderLayout;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.client.ui.PluginPanel;

/** RuneLite sidebar host for the source-neutral HapticScape Swing UI root. */
public final class RuneLiteHapticScapePluginPanel extends PluginPanel
{
	public RuneLiteHapticScapePluginPanel()
	{
		super();
		// HapticScapePanel owns its visual padding. Keep this wrapper structural only.
		setBorder(null);
		setLayout(new BorderLayout());
	}

	public JScrollPane getSidebarScrollPane()
	{
		return getScrollPane();
	}

	public void setContent(JPanel content)
	{
		removeAll();
		add(Objects.requireNonNull(content, "content"), BorderLayout.CENTER);
		revalidate();
		repaint();
	}
}
