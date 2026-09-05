package com.ashy0019.hapticscape.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;

/**
 * Keeps mouse activation of sidebar buttons from moving RuneLite's viewport.
 * Buttons remain focusable through normal keyboard traversal.
 */
final class SidebarActionFocusGuard extends ContainerAdapter implements AutoCloseable
{
	private final Set<Container> observedContainers = Collections.newSetFromMap(
		new IdentityHashMap<>()
	);
	private final Map<AbstractButton, Boolean> originalRequestFocus =
		new IdentityHashMap<>();
	private boolean closed;

	private SidebarActionFocusGuard(Container root)
	{
		configureTree(root);
	}

	static SidebarActionFocusGuard install(Container root)
	{
		if (root == null)
		{
			throw new IllegalArgumentException("The sidebar root is required");
		}
		return new SidebarActionFocusGuard(root);
	}

	@Override
	public void componentAdded(ContainerEvent event)
	{
		if (!closed)
		{
			configureTree(event.getChild());
		}
	}

	@Override
	public void componentRemoved(ContainerEvent event)
	{
		// Keep the original value until close in case a card is temporarily removed
		// and added again during a UI refresh.
	}

	@Override
	public void close()
	{
		if (closed)
		{
			return;
		}
		closed = true;
		for (Container container : observedContainers)
		{
			container.removeContainerListener(this);
		}
		for (Map.Entry<AbstractButton, Boolean> entry : originalRequestFocus.entrySet())
		{
			entry.getKey().setRequestFocusEnabled(entry.getValue());
		}
		observedContainers.clear();
		originalRequestFocus.clear();
	}

	private void configureTree(Component component)
	{
		if (component instanceof AbstractButton)
		{
			AbstractButton button = (AbstractButton) component;
			originalRequestFocus.putIfAbsent(button, button.isRequestFocusEnabled());
			button.setRequestFocusEnabled(false);
		}

		if (!(component instanceof Container))
		{
			return;
		}
		Container container = (Container) component;
		if (observedContainers.add(container))
		{
			container.addContainerListener(this);
		}
		for (Component child : container.getComponents())
		{
			configureTree(child);
		}
	}
}
