package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.SkillDescriptor;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class SkillsPanel extends JPanel
{
	private final SettingsChangeSink settingsSink;
	private final JLabel enabledSkillsValueLabel = new JLabel();
	private final SkillCatalog skillCatalog;
	private final Map<SkillDescriptor, JCheckBox> skillCheckBoxes = new LinkedHashMap<>();
	private final Map<SkillDescriptor, LockableCheckBoxBinding> lockBindings =
		new LinkedHashMap<>();
	private final JComboBox<SkillOutput> outputSelector =
		new JComboBox<>(SkillOutput.values());
	private final JButton allSkillsButton = new JButton("All");
	private final JButton noSkillsButton = new JButton("None");
	private final RemoteSessionManager sessionManager;
	private final SettingsLockDraft lockDraft;
	private final BooleanSupplier editingRemoteSubject;
	private final BooleanSupplier lockSelectionEnabled;
	private volatile SkillSelection hapticSkillSelection;
	private volatile SkillSelection clickSkillSelection;
	private boolean updatingSkillCheckBoxes;
	private boolean remoteReadOnly;

	SkillsPanel(
		SkillCatalog skillCatalog,
		SkillSelection hapticSkillSelection,
		SkillSelection clickSkillSelection,
		SettingsChangeSink settingsSink,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.skillCatalog = skillCatalog;
		this.hapticSkillSelection = hapticSkillSelection;
		this.clickSkillSelection = clickSkillSelection;
		this.settingsSink = settingsSink;
		this.sessionManager = sessionManager;
		this.lockDraft = lockDraft;
		this.editingRemoteSubject = editingRemoteSubject;
		this.lockSelectionEnabled = lockSelectionEnabled;
		setLayout(new BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		PanelUi.setFixedWidth(outputSelector, PanelUi.NUMERIC_CONTROL_WIDTH);
		outputSelector.setToolTipText("Choose which feedback channel these skill toggles control");
		outputSelector.addActionListener(event -> refreshSkillCheckBoxes());
		JPanel outputRow = new JPanel(new BorderLayout(4, 0));
		outputRow.add(new JLabel("Output"), BorderLayout.WEST);
		outputRow.add(outputSelector, BorderLayout.EAST);

		allSkillsButton.setToolTipText("Enable every skill for the selected output");
		allSkillsButton.addActionListener(event ->
		{
			if (isShift(event) && lockSelectionEnabled.getAsBoolean())
			{
				setAllSkillsLockSelected(true);
			}
			else
			{
				setAllSkillsEnabled(true);
			}
		});

		noSkillsButton.setToolTipText("Disable every skill for the selected output");
		noSkillsButton.addActionListener(event ->
		{
			if (isShift(event) && lockSelectionEnabled.getAsBoolean())
			{
				setAllSkillsLockSelected(false);
			}
			else
			{
				setAllSkillsEnabled(false);
			}
		});

		JPanel bulkSkillButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		bulkSkillButtons.add(allSkillsButton);
		bulkSkillButtons.add(noSkillsButton);

		JPanel skillsHeader = new JPanel(new BorderLayout(4, 0));
		skillsHeader.add(enabledSkillsValueLabel, BorderLayout.WEST);
		skillsHeader.add(bulkSkillButtons, BorderLayout.EAST);
		JPanel header = new JPanel(new GridLayout(2, 1, 0, 3));
		header.add(outputRow);
		header.add(skillsHeader);
		add(header, BorderLayout.NORTH);

		JPanel skillGrid = new JPanel(new GridLayout(0, 2, 4, 2));
		for (SkillDescriptor skill : skillCatalog.getSkills())
		{
			JCheckBox checkBox = new JCheckBox(
				skill.getDisplayName(),
				hapticSkillSelection.isEnabled(skill.getId())
			);
			LockableCheckBoxBinding binding = new LockableCheckBoxBinding(
				checkBox,
				() -> targetFor(selectedOutput(), skill),
				lockDraft,
				lockService,
				sessionManager::getLockSnapshot,
				editingRemoteSubject,
				lockSelectionEnabled,
				() -> selectionFor(selectedOutput()).isEnabled(skill.getId())
			);
			checkBox.addActionListener(event ->
			{
				if (!binding.handleAction(event))
				{
					setSkillEnabled(skill, checkBox.isSelected());
				}
			});
			skillCheckBoxes.put(skill, checkBox);
			lockBindings.put(skill, binding);
			skillGrid.add(checkBox);
		}
		add(skillGrid, BorderLayout.CENTER);
		updateEnabledSkillsLabel();
	}

	void applyDisplayedSelections(
		SkillSelection hapticSelection,
		SkillSelection clickSelection)
	{
		hapticSkillSelection = hapticSelection;
		clickSkillSelection = clickSelection;
		refreshSkillCheckBoxes();
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		refreshReadOnlyState();
	}

	boolean isHapticSkillEnabled(String skillId)
	{
		return hapticSkillSelection.isEnabled(skillId);
	}

	boolean isClickSkillEnabled(String skillId)
	{
		return clickSkillSelection.isEnabled(skillId);
	}

	boolean isSkillEnabled(String skillId)
	{
		return isHapticSkillEnabled(skillId);
	}

	private void setSkillEnabled(SkillDescriptor skill, boolean enabled)
	{
		if (remoteReadOnly || updatingSkillCheckBoxes)
		{
			return;
		}
		SkillOutput output = selectedOutput();
		SkillSelection updated = selectionFor(output).withEnabled(skill.getId(), enabled);
		setSelection(output, updated);
		persist(output, skill, updated);
		updateEnabledSkillsLabel();
	}

	private void setAllSkillsEnabled(boolean enabled)
	{
		if (remoteReadOnly)
		{
			return;
		}
		SkillOutput output = selectedOutput();
		SkillSelection updated = selectionFor(output);
		for (SkillDescriptor skill : skillCatalog.getSkills())
		{
			LockableCheckBoxBinding binding = lockBindings.get(skill);
			if (editingRemoteSubject.getAsBoolean() || !binding.isEditLocked())
			{
				updated = updated.withEnabled(skill.getId(), enabled);
			}
		}
		setSelection(output, updated);
		updatingSkillCheckBoxes = true;
		try
		{
			for (Map.Entry<SkillDescriptor, JCheckBox> entry : skillCheckBoxes.entrySet())
			{
				entry.getValue().setSelected(updated.isEnabled(entry.getKey().getId()));
			}
		}
		finally
		{
			updatingSkillCheckBoxes = false;
		}
		persist(output, null, updated);
		updateEnabledSkillsLabel();
	}

	private void setAllSkillsLockSelected(boolean selected)
	{
		Set<SettingsLockTarget> targets = new LinkedHashSet<>();
		SkillOutput output = selectedOutput();
		for (SkillDescriptor skill : skillCatalog.getSkills())
		{
			targets.add(targetFor(output, skill));
		}
		lockDraft.setAll(targets, selected);
		refreshReadOnlyState();
	}

	private void persist(
		SkillOutput output,
		SkillDescriptor skill,
		SkillSelection selection)
	{
		SettingsLockTarget target = skill == null ? null : targetFor(output, skill);
		settingsSink.set(
			target,
			output == SkillOutput.HAPTICS
				? HapticScapeConfig.DISABLED_SKILLS_KEY
				: HapticScapeConfig.CLICKER_DISABLED_SKILLS_KEY,
			selection.toConfigValue()
		);
	}

	private void updateEnabledSkillsLabel()
	{
		SkillOutput output = selectedOutput();
		int enabledCount = selectionFor(output).getEnabledCount(skillCatalog.getSkillIds());
		int skillCount = skillCatalog.getSkills().size();
		enabledSkillsValueLabel.setText(enabledCount + "/" + skillCount);
		enabledSkillsValueLabel.setToolTipText(
			enabledCount + " of " + skillCount + " skills enabled for " + output
		);
	}

	private void refreshSkillCheckBoxes()
	{
		SkillSelection selection = selectionFor(selectedOutput());
		updatingSkillCheckBoxes = true;
		try
		{
			for (Map.Entry<SkillDescriptor, JCheckBox> entry : skillCheckBoxes.entrySet())
			{
				entry.getValue().setSelected(selection.isEnabled(entry.getKey().getId()));
			}
		}
		finally
		{
			updatingSkillCheckBoxes = false;
		}
		updateEnabledSkillsLabel();
		refreshReadOnlyState();
	}

	private void refreshReadOnlyState()
	{
		for (Map.Entry<SkillDescriptor, JCheckBox> entry : skillCheckBoxes.entrySet())
		{
			LockableCheckBoxBinding binding = lockBindings.get(entry.getKey());
			binding.refresh();
			entry.getValue().setEnabled(!remoteReadOnly && !binding.isEditLocked());
		}
		allSkillsButton.setEnabled(!remoteReadOnly);
		noSkillsButton.setEnabled(!remoteReadOnly);
		// Output selector is navigation only, so it stays usable while remote-controlled.
		outputSelector.setEnabled(true);
	}

	private SkillOutput selectedOutput()
	{
		SkillOutput selected = (SkillOutput) outputSelector.getSelectedItem();
		return selected == null ? SkillOutput.HAPTICS : selected;
	}

	private SkillSelection selectionFor(SkillOutput output)
	{
		return output == SkillOutput.HAPTICS
			? hapticSkillSelection
			: clickSkillSelection;
	}

	private void setSelection(SkillOutput output, SkillSelection selection)
	{
		if (output == SkillOutput.HAPTICS)
		{
			hapticSkillSelection = selection;
		}
		else
		{
			clickSkillSelection = selection;
		}
	}

	private SettingsLockTarget targetFor(SkillOutput output, SkillDescriptor skill)
	{
		return output == SkillOutput.HAPTICS
			? SettingsLockCatalog.skillHaptics(skill.getId())
			: SettingsLockCatalog.skillClicks(skill.getId());
	}

	private static boolean isShift(ActionEvent event)
	{
		return (event.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
	}

	private enum SkillOutput
	{
		HAPTICS("Haptics"),
		CLICKER("Clicker");

		private final String displayName;

		SkillOutput(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}
