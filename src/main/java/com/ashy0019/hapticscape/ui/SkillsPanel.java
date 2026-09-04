package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.SkillSelection;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

final class SkillsPanel extends JPanel
{
	private final ConfigManager configManager;
	private final JLabel enabledSkillsValueLabel = new JLabel();
	private final Map<Skill, JCheckBox> skillCheckBoxes = new EnumMap<>(Skill.class);
	private final JComboBox<SkillOutput> outputSelector =
		new JComboBox<>(SkillOutput.values());
	private final JButton allSkillsButton = new JButton("All");
	private final JButton noSkillsButton = new JButton("None");
	private volatile SkillSelection hapticSkillSelection;
	private volatile SkillSelection clickSkillSelection;
	private boolean updatingSkillCheckBoxes;
	private boolean remoteReadOnly;

	SkillsPanel(
		SkillSelection hapticSkillSelection,
		SkillSelection clickSkillSelection,
		ConfigManager configManager)
	{
		this.hapticSkillSelection = hapticSkillSelection;
		this.clickSkillSelection = clickSkillSelection;
		this.configManager = configManager;
		setLayout(new BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		PanelUi.setFixedWidth(outputSelector, PanelUi.NUMERIC_CONTROL_WIDTH);
		outputSelector.setToolTipText("Choose which feedback channel these skill toggles control");
		outputSelector.addActionListener(event -> refreshSkillCheckBoxes());
		JPanel outputRow = new JPanel(new BorderLayout(4, 0));
		outputRow.add(new JLabel("Output"), BorderLayout.WEST);
		outputRow.add(outputSelector, BorderLayout.EAST);

		allSkillsButton.setToolTipText("Enable every skill for the selected output");
		allSkillsButton.addActionListener(event -> setAllSkillsEnabled(true));

		noSkillsButton.setToolTipText("Disable every skill for the selected output");
		noSkillsButton.addActionListener(event -> setAllSkillsEnabled(false));

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
		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			JCheckBox checkBox = new JCheckBox(
				skill.getName(),
				hapticSkillSelection.isEnabled(skill)
			);
			checkBox.addActionListener(event -> setSkillEnabled(skill, checkBox.isSelected()));
			skillCheckBoxes.put(skill, checkBox);
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

	boolean isHapticSkillEnabled(Skill skill)
	{
		return hapticSkillSelection.isEnabled(skill);
	}

	boolean isClickSkillEnabled(Skill skill)
	{
		return clickSkillSelection.isEnabled(skill);
	}

	boolean isSkillEnabled(Skill skill)
	{
		return isHapticSkillEnabled(skill);
	}

	private void setSkillEnabled(Skill skill, boolean enabled)
	{
		if (remoteReadOnly || updatingSkillCheckBoxes)
		{
			return;
		}
		SkillOutput output = selectedOutput();
		SkillSelection updated = selectionFor(output).withEnabled(skill, enabled);
		setSelection(output, updated);
		persist(output, updated);
		updateEnabledSkillsLabel();
	}

	private void setAllSkillsEnabled(boolean enabled)
	{
		if (remoteReadOnly)
		{
			return;
		}
		SkillOutput output = selectedOutput();
		SkillSelection updated = selectionFor(output).withAllEnabled(enabled);
		setSelection(output, updated);
		updatingSkillCheckBoxes = true;
		try
		{
			for (Map.Entry<Skill, JCheckBox> entry : skillCheckBoxes.entrySet())
			{
				entry.getValue().setSelected(updated.isEnabled(entry.getKey()));
			}
		}
		finally
		{
			updatingSkillCheckBoxes = false;
		}
		persist(output, updated);
		updateEnabledSkillsLabel();
	}

	private void persist(SkillOutput output, SkillSelection selection)
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			output == SkillOutput.HAPTICS
				? HapticScapeConfig.DISABLED_SKILLS_KEY
				: HapticScapeConfig.CLICKER_DISABLED_SKILLS_KEY,
			selection.toConfigValue()
		);
	}

	private void updateEnabledSkillsLabel()
	{
		SkillOutput output = selectedOutput();
		int enabledCount = selectionFor(output).getEnabledCount();
		int skillCount = SkillSelection.getSelectableSkills().size();
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
			for (Map.Entry<Skill, JCheckBox> entry : skillCheckBoxes.entrySet())
			{
				entry.getValue().setSelected(selection.isEnabled(entry.getKey()));
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
		for (JCheckBox checkBox : skillCheckBoxes.values())
		{
			checkBox.setEnabled(!remoteReadOnly);
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
