package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteLockAndVaultUiTest
{
	@Test
	public void lockPreparationShowsOnlyActionsForItsCurrentState()
	{
		RemoteLockPreparationPanel.LockView empty =
			RemoteLockPreparationPanel.viewFor(RemoteLockState.INACTIVE, 0);
		assertEquals("Nothing selected", empty.getTitle());
		assertTrue(empty.showsOpenSubject());
		assertFalse(empty.showsRequest());
		assertFalse(empty.showsCancel());

		RemoteLockPreparationPanel.LockView selected =
			RemoteLockPreparationPanel.viewFor(RemoteLockState.INACTIVE, 3);
		assertEquals("3 settings selected", selected.getTitle());
		assertTrue(selected.showsOpenSubject());
		assertTrue(selected.showsRequest());
		assertFalse(selected.showsCancel());

		RemoteLockPreparationPanel.LockView waiting =
			RemoteLockPreparationPanel.viewFor(RemoteLockState.AWAITING_APPROVAL, 3);
		assertEquals("Waiting for approval", waiting.getTitle());
		assertFalse(waiting.showsOpenSubject());
		assertFalse(waiting.showsRequest());
		assertTrue(waiting.showsCancel());
		assertEquals("Cancel request", waiting.getCancelLabel());

		RemoteLockPreparationPanel.LockView armed =
			RemoteLockPreparationPanel.viewFor(RemoteLockState.ARMED, 3);
		assertEquals("Post-session lock armed", armed.getTitle());
		assertFalse(armed.showsRequest());
		assertTrue(armed.showsCancel());
		assertEquals("Cancel lock", armed.getCancelLabel());

		RemoteLockPreparationPanel.LockView declined =
			RemoteLockPreparationPanel.viewFor(RemoteLockState.DECLINED, 3);
		assertTrue(declined.showsOpenSubject());
		assertTrue(declined.showsRequest());
		assertTrue(declined.showsCancel());
		assertEquals("Start over", declined.getCancelLabel());
	}

	@Test
	public void lockPreparationDoesNotRebuildActionsForCountOnlyChanges() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RemoteLockPreparationPanel panel = new RemoteLockPreparationPanel(
				() -> { },
				() -> { },
				() -> { },
				() -> { },
				() -> { }
			);
			RemoteLockSnapshot snapshot = RemoteLockSnapshot.inactive();
			panel.apply(snapshot, 1, true, true, false, false, true, true, "");

			JPanel actions = (JPanel) findByName(panel, "remoteLockActions");
			AtomicInteger structuralChanges = new AtomicInteger();
			actions.addContainerListener(new ContainerAdapter()
			{
				@Override
				public void componentAdded(ContainerEvent event)
				{
					structuralChanges.incrementAndGet();
				}

				@Override
				public void componentRemoved(ContainerEvent event)
				{
					structuralChanges.incrementAndGet();
				}
			});

			panel.apply(snapshot, 2, true, true, false, false, true, true, "");
			assertEquals(0, structuralChanges.get());
		});
	}

	@Test
	public void vaultSummaryDistinguishesUnavailableEmptyAndManageableStates()
	{
		SavedUnlockKeyVaultState unavailable = SavedUnlockKeyVaultState.from(
			false,
			"Saved Unlock Keys require the Windows client",
			0
		);
		assertEquals(
			"Saved Unlock Keys require the Windows client",
			unavailable.getStatus()
		);
		assertFalse(unavailable.isManageable());

		SavedUnlockKeyVaultState empty = SavedUnlockKeyVaultState.from(true, "", 0);
		assertEquals("No accepted unlock keys saved", empty.getStatus());
		assertFalse(empty.isManageable());

		SavedUnlockKeyVaultState one = SavedUnlockKeyVaultState.from(true, "", 1);
		assertEquals("1 saved unlock key", one.getStatus());
		assertTrue(one.isManageable());

		SavedUnlockKeyVaultState several = SavedUnlockKeyVaultState.from(true, "", 4);
		assertEquals("4 saved unlock keys", several.getStatus());
		assertTrue(several.isManageable());
	}

	@Test
	public void vaultManagerStacksAtCompactWidths()
	{
		assertEquals(1, SavedUnlockKeyVaultWorkspacePanel.layoutModeForWidth(759));
		assertEquals(2, SavedUnlockKeyVaultWorkspacePanel.layoutModeForWidth(760));
	}

	private static Component findByName(Container root, String name)
	{
		for (Component component : root.getComponents())
		{
			if (name.equals(component.getName()))
			{
				return component;
			}
			if (component instanceof Container)
			{
				Component nested = findByName((Container) component, name);
				if (nested != null)
				{
					return nested;
				}
			}
		}
		return null;
	}
}
