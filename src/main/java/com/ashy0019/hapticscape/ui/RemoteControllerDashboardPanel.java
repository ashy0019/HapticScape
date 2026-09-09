package com.ashy0019.hapticscape.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JPanel;

/** Gives live actions priority while stacking cleanly in compact windows. */
final class RemoteControllerDashboardPanel extends JPanel
{
	private static final int COLUMN_BREAKPOINT = 900;

	private final Component actions;
	private final Component subjectWorkspace;
	private int layoutMode = -1;

	RemoteControllerDashboardPanel(Component actions, Component subjectWorkspace)
	{
		this.actions = actions;
		this.subjectWorkspace = subjectWorkspace;
		setName("remoteControllerDashboard");
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
		addSection(actions, 0);
		addSection(subjectWorkspace, 1);

		GridBagConstraints remainder = new GridBagConstraints();
		remainder.gridx = 0;
		remainder.gridy = layoutMode == 1 ? 2 : 1;
		remainder.gridwidth = layoutMode;
		remainder.weighty = 1.0;
		remainder.fill = GridBagConstraints.VERTICAL;
		add(new JPanel(), remainder);
		revalidate();
		repaint();
	}

	private void addSection(Component component, int index)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = layoutMode == 1 ? 0 : index;
		constraints.gridy = layoutMode == 1 ? index : 0;
		constraints.weightx = layoutMode == 1 ? 1.0 : index == 0 ? 2.0 : 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = layoutMode == 1
			? new Insets(0, 0, index == 0 ? 8 : 0, 0)
			: new Insets(0, 0, 0, index == 0 ? 8 : 0);
		add(component, constraints);
	}

	static int layoutModeForWidth(int width)
	{
		return width >= COLUMN_BREAKPOINT ? 2 : 1;
	}
}
