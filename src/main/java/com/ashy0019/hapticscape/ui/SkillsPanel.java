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
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

final class SkillsPanel extends JPanel
{
	private final ConfigManager configManager;
	private final JLabel enabledSkillsValueLabel = new JLabel();
	private final Map<Skill, JCheckBox> skillCheckBoxes = new EnumMap<>(Skill.class);
	private volatile SkillSelection skillSelection;
	private boolean updatingSkillCheckBoxes;

	SkillsPanel(SkillSelection skillSelection, ConfigManager configManager)
	{
		this.skillSelection = skillSelection;
		this.configManager = configManager;
		setLayout(new BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JButton allSkillsButton = new JButton("All");
		allSkillsButton.setToolTipText("Enable XP feedback for every skill");
		allSkillsButton.addActionListener(event -> setAllSkillsEnabled(true));

		JButton noSkillsButton = new JButton("None");
		noSkillsButton.setToolTipText("Disable XP feedback for every skill");
		noSkillsButton.addActionListener(event -> setAllSkillsEnabled(false));

		JPanel bulkSkillButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		bulkSkillButtons.add(allSkillsButton);
		bulkSkillButtons.add(noSkillsButton);

		JPanel skillsHeader = new JPanel(new BorderLayout(4, 0));
		skillsHeader.add(enabledSkillsValueLabel, BorderLayout.WEST);
		skillsHeader.add(bulkSkillButtons, BorderLayout.EAST);
		add(skillsHeader, BorderLayout.NORTH);

		JPanel skillGrid = new JPanel(new GridLayout(0, 2, 4, 2));
		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			JCheckBox checkBox = new JCheckBox(
				skill.getName(),
				skillSelection.isEnabled(skill)
			);
			checkBox.addActionListener(event -> setSkillEnabled(skill, checkBox.isSelected()));
			skillCheckBoxes.put(skill, checkBox);
			skillGrid.add(checkBox);
		}
		add(skillGrid, BorderLayout.CENTER);
		updateEnabledSkillsLabel();
	}

	boolean isSkillEnabled(Skill skill)
	{
		return skillSelection.isEnabled(skill);
	}

	private void setSkillEnabled(Skill skill, boolean enabled)
	{
		if (updatingSkillCheckBoxes)
		{
			return;
		}
		skillSelection = skillSelection.withEnabled(skill, enabled);
		persist();
		updateEnabledSkillsLabel();
	}

	private void setAllSkillsEnabled(boolean enabled)
	{
		SkillSelection updated = skillSelection.withAllEnabled(enabled);
		skillSelection = updated;
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
		persist();
		updateEnabledSkillsLabel();
	}

	private void persist()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.DISABLED_SKILLS_KEY,
			skillSelection.toConfigValue()
		);
	}

	private void updateEnabledSkillsLabel()
	{
		int enabledCount = skillSelection.getEnabledCount();
		int skillCount = SkillSelection.getSelectableSkills().size();
		enabledSkillsValueLabel.setText(enabledCount + "/" + skillCount);
		enabledSkillsValueLabel.setToolTipText(
			enabledCount + " of " + skillCount + " skills enabled"
		);
	}
}
