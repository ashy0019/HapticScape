package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

final class PanelUi
{
	static final int NUMERIC_CONTROL_WIDTH = 96;
	static final int SELECTOR_CONTROL_WIDTH = 108;
	static final String DURATION_LABEL = "Duration (ms)";

	private PanelUi()
	{
	}

	static JComboBox<HapticPatternSelection> createPatternComboBox(
		Supplier<CustomPatternLibrary> librarySupplier)
	{
		CustomPatternLibrary library = librarySupplier.get();
		HapticPatternSelection[] choices = HapticPatternSelection
			.availableSelections(library)
			.toArray(new HapticPatternSelection[0]);
		JComboBox<HapticPatternSelection> comboBox = new JComboBox<>(choices);
		comboBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
			{
				super.getListCellRendererComponent(
					list,
					value,
					index,
					isSelected,
					cellHasFocus
				);
				setText(value instanceof HapticPatternSelection
					? ((HapticPatternSelection) value).getDisplayName(librarySupplier.get())
					: "");
				return this;
			}
		});
		setFixedWidth(comboBox, SELECTOR_CONTROL_WIDTH);
		return comboBox;
	}

	static void setPatternChoices(
		JComboBox<HapticPatternSelection> comboBox,
		HapticPatternSelection selected,
		CustomPatternLibrary library)
	{
		HapticPatternSelection[] choices = HapticPatternSelection
			.availableSelections(library)
			.toArray(new HapticPatternSelection[0]);
		comboBox.setModel(new DefaultComboBoxModel<>(choices));
		comboBox.setSelectedItem(selected.resolveAgainst(library));
		comboBox.repaint();
	}

	static void setFixedWidth(JComponent component, int width)
	{
		Dimension preferredSize = component.getPreferredSize();
		Dimension fixedSize = new Dimension(width, preferredSize.height);
		component.setPreferredSize(fixedSize);
		component.setMinimumSize(fixedSize);
		component.setMaximumSize(fixedSize);
	}

	/**
	 * Keeps a component at its current preferred height while allowing its width
	 * to follow the surrounding BoxLayout. Use this only for controls whose height
	 * is intentionally stable after construction.
	 */
	static void addPreferredHeightComponent(JPanel panel, JComponent component)
	{
		Dimension preferredSize = component.getPreferredSize();
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
		panel.add(component);
	}

	/**
	 * Supplies useful vertical space without imposing a preferred or minimum
	 * width on a component inside a responsive workspace.
	 */
	static void setFlexibleWidthHeightHint(
		JComponent component,
		int preferredHeight,
		int minimumHeight)
	{
		component.setPreferredSize(new Dimension(0, preferredHeight));
		component.setMinimumSize(new Dimension(0, minimumHeight));
	}

	/**
	 * Adds content whose preferred height may legitimately change after construction.
	 * The surrounding BoxLayout still uses the preferred height, but does not retain a
	 * stale maximum that can clip a tab page or CardLayout child later.
	 */
	static void addFlexibleVerticalComponent(JPanel panel, JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
		panel.add(component);
	}

	static void addCompactTab(JTabbedPane tabs, String title, Component component)
	{
		tabs.addTab(title, component);
		int tabIndex = tabs.getTabCount() - 1;
		JLabel label = new JLabel(title, SwingConstants.CENTER);
		label.setFont(label.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
		int width = Math.max(58, Math.min(180, label.getFontMetrics(label.getFont())
			.stringWidth(title) + 18));
		label.setPreferredSize(new Dimension(width, 18));
		tabs.setTabComponentAt(tabIndex, label);
	}
}
