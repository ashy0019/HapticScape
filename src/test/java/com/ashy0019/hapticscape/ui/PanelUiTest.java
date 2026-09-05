package com.ashy0019.hapticscape.ui;

import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PanelUiTest
{
	@Test
	public void flexibleVerticalComponentAllowsTabHeightToGrow() throws Exception
	{
		AtomicReference<Dimension> initialSize = new AtomicReference<>();
		AtomicReference<Dimension> grownSize = new AtomicReference<>();
		AtomicReference<Dimension> maximumSize = new AtomicReference<>();
		AtomicReference<Dimension> alternateTabSize = new AtomicReference<>();
		AtomicReference<Dimension> laidOutContentSize = new AtomicReference<>();

		SwingUtilities.invokeAndWait(() ->
		{
			JPanel host = new JPanel();
			host.setLayout(new BoxLayout(host, BoxLayout.Y_AXIS));

			JPanel shortTab = new JPanel();
			shortTab.setPreferredSize(new Dimension(180, 100));
			JPanel growingTab = new JPanel();
			growingTab.setPreferredSize(new Dimension(180, 160));
			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab("Skills", shortTab);
			tabs.addTab("Click", growingTab);
			PanelUi.addFlexibleVerticalComponent(host, tabs);

			initialSize.set(tabs.getPreferredSize());
			growingTab.setPreferredSize(new Dimension(180, 320));
			growingTab.revalidate();
			grownSize.set(tabs.getPreferredSize());
			maximumSize.set(tabs.getMaximumSize());

			tabs.setSelectedComponent(growingTab);
			alternateTabSize.set(tabs.getPreferredSize());
			host.setSize(host.getPreferredSize());
			host.doLayout();
			tabs.doLayout();
			laidOutContentSize.set(growingTab.getSize());
		});

		assertTrue(grownSize.get().height > initialSize.get().height);
		assertTrue(maximumSize.get().height >= grownSize.get().height);
		assertTrue(alternateTabSize.get().height >= grownSize.get().height);
		assertTrue(laidOutContentSize.get().height >= 320);
	}
}
