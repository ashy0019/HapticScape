package com.ashy0019.hapticscape.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Compact, responsive navigation shell for the standalone desktop application. */
final class WorkspaceShell extends JPanel
{
	private static final int COMPACT_BREAKPOINT = 820;

	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JPanel navigationHost = new JPanel(new BorderLayout());
	private final JPanel navigation = new JPanel();
	private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();
	private final ButtonGroup buttonGroup = new ButtonGroup();
	private Consumer<String> userSelectionAction = ignored -> { };
	private String selectedId;
	private boolean compact;

	WorkspaceShell()
	{
		super(new BorderLayout());
		setName("workspaceShell");
		navigationHost.setName("workspaceNavigationHost");
		navigation.setName("workspaceNavigation");
		navigationHost.add(navigation, BorderLayout.NORTH);
		add(navigationHost, BorderLayout.WEST);
		add(content, BorderLayout.CENTER);
		setNavigationOrientation(false);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				refreshResponsiveLayout();
			}
		});
	}

	void addWorkspace(String id, String label, Component component)
	{
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(component, "component");
		if (buttons.containsKey(id))
		{
			throw new IllegalArgumentException("Duplicate workspace: " + id);
		}
		JToggleButton button = new JToggleButton(label);
		button.setName("workspace-" + id);
		button.setHorizontalAlignment(JButton.LEFT);
		button.setFocusPainted(false);
		button.addActionListener(event ->
		{
			showWorkspace(id);
			userSelectionAction.accept(id);
		});
		buttons.put(id, button);
		buttonGroup.add(button);
		navigation.add(button);
		content.add(component, id);
		if (selectedId == null)
		{
			showWorkspace(id);
		}
	}

	void setUserSelectionAction(Consumer<String> action)
	{
		userSelectionAction = Objects.requireNonNull(action, "action");
	}

	void showWorkspace(String id)
	{
		JToggleButton button = buttons.get(id);
		if (button == null)
		{
			throw new IllegalArgumentException("Unknown workspace: " + id);
		}
		selectedId = id;
		button.setSelected(true);
		cards.show(content, id);
		content.revalidate();
		content.repaint();
	}

	String getSelectedWorkspace()
	{
		return selectedId;
	}

	private void refreshResponsiveLayout()
	{
		boolean shouldBeCompact = getWidth() > 0 && getWidth() < COMPACT_BREAKPOINT;
		if (shouldBeCompact == compact)
		{
			return;
		}
		compact = shouldBeCompact;
		remove(navigationHost);
		add(navigationHost, compact ? BorderLayout.NORTH : BorderLayout.WEST);
		setNavigationOrientation(compact);
		revalidate();
		repaint();
	}

	private void setNavigationOrientation(boolean horizontal)
	{
		navigation.setLayout(horizontal
			? new GridLayout(1, 0, 0, 0)
			: new GridLayout(0, 1, 0, 0));
		navigationHost.setBorder(horizontal
			? BorderFactory.createMatteBorder(0, 0, 1, 0, navigationHost.getForeground())
			: BorderFactory.createMatteBorder(0, 0, 0, 1, navigationHost.getForeground()));
		if (!horizontal)
		{
			navigationHost.setPreferredSize(new Dimension(152, 0));
		}
		else
		{
			navigationHost.setPreferredSize(null);
		}
	}
}
