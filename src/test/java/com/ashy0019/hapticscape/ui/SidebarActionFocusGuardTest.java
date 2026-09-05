package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.junit.Test;

public class SidebarActionFocusGuardTest
{
	@Test
	public void preventsMouseFocusRequestsWithoutDisablingKeyboardFocus()
	{
		JPanel page = new JPanel();
		JCheckBox checkBox = new JCheckBox("Enabled");
		JButton newPatternButton = new JButton("New");
		page.add(checkBox);
		page.add(newPatternButton);

		try (SidebarActionFocusGuard ignored = SidebarActionFocusGuard.install(page))
		{
			assertFalse(checkBox.isRequestFocusEnabled());
			assertFalse(newPatternButton.isRequestFocusEnabled());
			assertTrue(checkBox.isFocusable());
			assertTrue(newPatternButton.isFocusable());
		}

		assertTrue(checkBox.isRequestFocusEnabled());
		assertTrue(newPatternButton.isRequestFocusEnabled());
	}

	@Test
	public void configuresButtonsAddedAfterInstallation()
	{
		JPanel page = new JPanel();
		try (SidebarActionFocusGuard ignored = SidebarActionFocusGuard.install(page))
		{
			JPanel addedSection = new JPanel();
			JCheckBox addedCheckBox = new JCheckBox("Later");
			addedSection.add(addedCheckBox);
			page.add(addedSection);

			assertFalse(addedCheckBox.isRequestFocusEnabled());
		}
	}
}
