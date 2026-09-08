package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ComponentEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class WorkspaceShellTest
{
	@Test
	public void navigationSelectsCardsAndReportsOnlyUserSelections() throws Exception
	{
		AtomicReference<String> selected = new AtomicReference<>();
		WorkspaceShell shell = onEdt(() ->
		{
			WorkspaceShell created = new WorkspaceShell();
			created.addWorkspace("gameplay", "Gameplay", new JPanel());
			created.addWorkspace("remote", "Remote Play", new JPanel());
			created.setUserSelectionAction(selected::set);
			return created;
		});
		assertEquals("gameplay", shell.getSelectedWorkspace());
		onEdt(() ->
		{
			component(shell, "workspace-remote", AbstractButton.class).doClick();
			return null;
		});
		assertEquals("remote", shell.getSelectedWorkspace());
		assertEquals("remote", selected.get());

		onEdt(() ->
		{
			shell.showWorkspace("gameplay");
			return null;
		});
		assertEquals("remote", selected.get());
	}

	@Test
	public void navigationMovesAboveContentAtCompactWidth() throws Exception
	{
		WorkspaceShell shell = onEdt(() ->
		{
			WorkspaceShell created = new WorkspaceShell();
			created.addWorkspace("gameplay", "Gameplay", new JPanel());
			created.addWorkspace("remote", "Remote Play", new JPanel());
			return created;
		});
		Container navigationHost = component(
			shell,
			"workspaceNavigationHost",
			Container.class
		);
		onEdt(() ->
		{
			shell.setSize(500, 600);
			shell.dispatchEvent(new ComponentEvent(shell, ComponentEvent.COMPONENT_RESIZED));
			return null;
		});
		assertEquals(
			BorderLayout.NORTH,
			((BorderLayout) shell.getLayout()).getConstraints(navigationHost)
		);
		onEdt(() ->
		{
			shell.setSize(900, 600);
			shell.dispatchEvent(new ComponentEvent(shell, ComponentEvent.COMPONENT_RESIZED));
			return null;
		});
		assertEquals(
			BorderLayout.WEST,
			((BorderLayout) shell.getLayout()).getConstraints(navigationHost)
		);
		assertTrue(navigationHost.getPreferredSize().width >= 140);
	}

	private static <T extends Component> T component(
		Container root,
		String name,
		Class<T> type)
	{
		for (Component candidate : root.getComponents())
		{
			if (name.equals(candidate.getName()) && type.isInstance(candidate))
			{
				return type.cast(candidate);
			}
			if (candidate instanceof Container)
			{
				try
				{
					return component((Container) candidate, name, type);
				}
				catch (AssertionError ignored)
				{
					// Continue through the remaining branches.
				}
			}
		}
		throw new AssertionError("Missing component: " + name);
	}

	private static <T> T onEdt(Callable<T> operation) throws Exception
	{
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				result.set(operation.call());
			}
			catch (Exception error)
			{
				failure.set(error);
			}
		});
		if (failure.get() != null)
		{
			throw failure.get();
		}
		return result.get();
	}
}
