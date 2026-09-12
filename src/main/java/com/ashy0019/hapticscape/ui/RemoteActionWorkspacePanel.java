package com.ashy0019.hapticscape.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JPanel;

/** Responsive action surface which never hides controls behind another mode. */
final class RemoteActionWorkspacePanel extends JPanel
{
	private static final int COLUMN_BREAKPOINT = 680;

	private final Component haptics;
	private final Component communication;
	private int layoutMode = -1;

	RemoteActionWorkspacePanel(Component haptics, Component communication)
	{
		this.haptics = haptics;
		this.communication = communication;
		setName("remoteActionWorkspace");
		setLayout(new GridBagLayout());
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflow();
			}
		});
		reflow();
	}

	private void reflow()
	{
		int nextMode = layoutModeForWidth(getWidth());
		if (nextMode == layoutMode)
		{
			return;
		}
		layoutMode = nextMode;
		removeAll();
		addSection(haptics, 0);
		addSection(communication, 1);
		revalidate();
		repaint();
	}

	private void addSection(Component component, int index)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = layoutMode == 1 ? 0 : index;
		constraints.gridy = layoutMode == 1 ? index : 0;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = layoutMode == 1
			? new Insets(0, 0, index == 0 ? 6 : 0, 0)
			: new Insets(0, 0, 0, index == 0 ? 6 : 0);
		add(component, constraints);
	}

	static int layoutModeForWidth(int width)
	{
		return width >= COLUMN_BREAKPOINT ? 2 : 1;
	}
}
