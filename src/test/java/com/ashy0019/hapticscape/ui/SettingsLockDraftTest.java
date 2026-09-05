package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import com.google.gson.Gson;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsLockDraftTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void shiftClickSelectsLockWithoutChangingCheckboxValue() throws Exception
	{
		SettingsLockDraft draft = new SettingsLockDraft();
		JCheckBox checkBox = new JCheckBox("Attack", true);
		SettingsLockService lockService = newLockService("shift-click.json");
		AtomicReference<LockableCheckBoxBinding> binding = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> binding.set(new LockableCheckBoxBinding(
			checkBox,
			SettingsLockCatalog.skillHaptics(Skill.ATTACK),
			draft,
			lockService,
			RemoteLockSnapshot::inactive,
			() -> true,
			() -> true,
			() -> true
		)));

		SwingUtilities.invokeAndWait(() ->
		{
			Dimension preferredSize = checkBox.getPreferredSize();
			checkBox.setSelected(false);
			assertTrue(binding.get().handleAction(shiftAction(checkBox)));
			assertTrue(checkBox.isSelected());
			assertTrue(draft.contains(SettingsLockCatalog.skillHaptics(Skill.ATTACK)));
			assertEquals(preferredSize, checkBox.getPreferredSize());

			checkBox.setSelected(false);
			assertTrue(binding.get().handleAction(shiftAction(checkBox)));
			assertTrue(checkBox.isSelected());
			assertFalse(draft.contains(SettingsLockCatalog.skillHaptics(Skill.ATTACK)));
			assertEquals(preferredSize, checkBox.getPreferredSize());
		});
	}

	@Test
	public void dynamicSkillOutputTargetsRemainIndependent() throws Exception
	{
		SettingsLockDraft draft = new SettingsLockDraft();
		JCheckBox checkBox = new JCheckBox("Fishing", true);
		SettingsLockService lockService = newLockService("dynamic-target.json");
		AtomicReference<SettingsLockTarget> target = new AtomicReference<>(
			SettingsLockCatalog.skillHaptics(Skill.FISHING)
		);
		AtomicReference<LockableCheckBoxBinding> binding = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> binding.set(new LockableCheckBoxBinding(
			checkBox,
			target::get,
			draft,
			lockService,
			RemoteLockSnapshot::inactive,
			() -> true,
			() -> true,
			() -> true
		)));

		SwingUtilities.invokeAndWait(() ->
		{
			assertTrue(binding.get().handleAction(shiftAction(checkBox)));
			target.set(SettingsLockCatalog.skillClicks(Skill.FISHING));
			assertTrue(binding.get().handleAction(shiftAction(checkBox)));
		});

		assertTrue(draft.contains(SettingsLockCatalog.skillHaptics(Skill.FISHING)));
		assertTrue(draft.contains(SettingsLockCatalog.skillClicks(Skill.FISHING)));
	}

	@Test
	public void shiftClickCannotMutateWhileAnotherProposalIsActive() throws Exception
	{
		SettingsLockDraft draft = new SettingsLockDraft();
		JCheckBox checkBox = new JCheckBox("Prayer", true);
		SettingsLockService lockService = newLockService("inactive-selection.json");
		AtomicReference<LockableCheckBoxBinding> binding = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> binding.set(new LockableCheckBoxBinding(
			checkBox,
			SettingsLockCatalog.skillHaptics(Skill.PRAYER),
			draft,
			lockService,
			RemoteLockSnapshot::inactive,
			() -> true,
			() -> false,
			() -> true
		)));

		SwingUtilities.invokeAndWait(() ->
		{
			checkBox.setSelected(false);
			assertTrue(binding.get().handleAction(shiftAction(checkBox)));
			assertTrue(checkBox.isSelected());
		});
		assertTrue(draft.snapshot().isEmpty());
	}

	private static ActionEvent shiftAction(JCheckBox checkBox)
	{
		return new ActionEvent(
			checkBox,
			ActionEvent.ACTION_PERFORMED,
			"",
			ActionEvent.SHIFT_MASK
		);
	}

	private SettingsLockService newLockService(String name) throws Exception
	{
		Constructor<SettingsLockService> constructor = SettingsLockService.class
			.getDeclaredConstructor(Gson.class, Path.class);
		constructor.setAccessible(true);
		return constructor.newInstance(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve(name)
		);
	}
}
