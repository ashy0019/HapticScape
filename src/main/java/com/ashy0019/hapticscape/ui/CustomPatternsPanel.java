package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternEntry;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;

/**
 * The Custom tab boundary. PatternForgePanel remains focused on editing one
 * library, while this component keeps forge details out of the main shell.
 */
final class CustomPatternsPanel extends JPanel
{
	private final PatternForgePanel forgePanel;

	CustomPatternsPanel(
		CustomPatternLibrary library,
		ConfigManager configManager,
		Consumer<CustomPatternEntry> previewAction,
		Consumer<CustomPatternLibrary> libraryChangeAction)
	{
		super(new BorderLayout());
		forgePanel = new PatternForgePanel(
			library,
			configManager,
			previewAction,
			libraryChangeAction
		);
		add(forgePanel, BorderLayout.CENTER);
	}

	void applyDisplayedLibrary(CustomPatternLibrary library)
	{
		forgePanel.applyDisplayedLibrary(library);
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		forgePanel.setRemoteReadOnly(remoteReadOnly);
	}

	void setConnected(boolean connected)
	{
		forgePanel.setConnected(connected);
	}

	void close()
	{
		forgePanel.close();
	}
}
