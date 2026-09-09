package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AlertsPanelTest
{
	@Test
	public void choosesLayoutForAvailableDesktopWidth()
	{
		assertEquals(1, AlertsPanel.layoutModeForWidth(500));
		assertEquals(2, AlertsPanel.layoutModeForWidth(720));
		assertEquals(2, AlertsPanel.layoutModeForWidth(1119));
		assertEquals(3, AlertsPanel.layoutModeForWidth(1120));
	}
}
