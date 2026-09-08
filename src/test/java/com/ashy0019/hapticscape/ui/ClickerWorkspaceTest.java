package com.ashy0019.hapticscape.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClickerWorkspaceTest
{
	@Test
	public void reflowsTheWorkspaceAtDesktopBreakpoints()
	{
		assertEquals(1, ClickerPanel.layoutModeForWidth(500));
		assertEquals(2, ClickerPanel.layoutModeForWidth(760));
		assertEquals(2, ClickerPanel.layoutModeForWidth(1249));
		assertEquals(3, ClickerPanel.layoutModeForWidth(1250));
	}

	@Test
	public void reflowsThePhraseEditorInsideItsAvailableWidth()
	{
		assertEquals(1, ClickerPhraseRulesPanel.layoutModeForWidth(500));
		assertEquals(1, ClickerPhraseRulesPanel.layoutModeForWidth(619));
		assertEquals(2, ClickerPhraseRulesPanel.layoutModeForWidth(620));
	}
}
