package com.ashy0019.hapticscape.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/** Reflows independent settings sections without stretching them vertically. */
final class ResponsiveColumnsPanel extends JPanel
{
	private final List<Component> sections = new ArrayList<>();
	private int columns;

	ResponsiveColumnsPanel(Component... components)
	{
		setLayout(new GridBagLayout());
		for (Component component : components)
		{
			sections.add(component);
		}
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
		int width = getWidth();
		int desired = width >= 1080 ? 3 : width >= 720 ? 2 : 1;
		if (desired == columns)
		{
			return;
		}
		columns = desired;
		removeAll();
		for (int index = 0; index < sections.size(); index++)
		{
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridx = index % columns;
			constraints.gridy = index / columns;
			constraints.weightx = 1.0;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.anchor = GridBagConstraints.NORTHWEST;
			constraints.insets = new Insets(0, 0, 8, index % columns == columns - 1 ? 0 : 8);
			add(sections.get(index), constraints);
		}
		GridBagConstraints remainder = new GridBagConstraints();
		remainder.gridx = 0;
		remainder.gridy = (sections.size() + columns - 1) / columns;
		remainder.gridwidth = columns;
		remainder.weighty = 1.0;
		remainder.fill = GridBagConstraints.VERTICAL;
		add(new JPanel(), remainder);
		revalidate();
		repaint();
	}
}
