package com.ashy0019.hapticscape.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClickerWorkspaceTest
{
	@Test
	public void reflowsThePhraseEditorInsideItsAvailableWidth()
	{
		assertEquals(1, ClickerPhraseRulesPanel.layoutModeForWidth(500));
		assertEquals(1, ClickerPhraseRulesPanel.layoutModeForWidth(619));
		assertEquals(2, ClickerPhraseRulesPanel.layoutModeForWidth(620));
	}
}
