package com.ashy0019.hapticscape.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JPanel;

/** Responsive master-detail layout for disconnected saved-key management. */
final class SavedUnlockKeyVaultWorkspacePanel extends JPanel
{
	private static final int COLUMN_BREAKPOINT = 760;

	private final Component keyList;
	private final Component details;
	private int layoutMode = -1;

	SavedUnlockKeyVaultWorkspacePanel(Component keyList, Component details)
	{
		this.keyList = keyList;
		this.details = details;
		setName("savedUnlockKeyVaultWorkspace");
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
		addSection(keyList, 0);
		addSection(details, 1);
		revalidate();
		repaint();
	}

	private void addSection(Component component, int index)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = layoutMode == 1 ? 0 : index;
		constraints.gridy = layoutMode == 1 ? index : 0;
		constraints.weightx = layoutMode == 1 ? 1.0 : index == 0 ? 0.4 : 0.6;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = layoutMode == 1
			? new Insets(0, 0, index == 0 ? 7 : 0, 0)
			: new Insets(0, 0, 0, index == 0 ? 7 : 0);
		add(component, constraints);
	}

	static int layoutModeForWidth(int width)
	{
		return width >= COLUMN_BREAKPOINT ? 2 : 1;
	}
}
