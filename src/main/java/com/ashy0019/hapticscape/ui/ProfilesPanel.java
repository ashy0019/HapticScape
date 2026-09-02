package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

final class ProfilesPanel extends JPanel
{
	private final ConfigManager configManager;
	private final Supplier<XpFeedbackSettings> globalSettingsSupplier;
	private final Supplier<CustomPatternLibrary> customPatternsSupplier;
	private final JComboBox<Skill> skillComboBox;
	private final JCheckBox useGlobalCheckBox = new JCheckBox("Use global XP settings");
	private final JSpinner minimumXpSpinner;
	private final JSlider intensitySlider;
	private final JLabel intensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> patternComboBox;
	private final JSpinner durationSpinner;
	private final JButton testButton = new JButton("Test selected skill");

	private volatile SkillFeedbackProfiles profiles;
	private volatile Skill selectedSkill;
	private boolean updatingControls;
	private boolean updatingPatternChoices;
	private boolean connected;

	ProfilesPanel(
		SkillFeedbackProfiles profiles,
		ConfigManager configManager,
		Supplier<XpFeedbackSettings> globalSettingsSupplier,
		Supplier<CustomPatternLibrary> customPatternsSupplier,
		Runnable testAction)
	{
		this.profiles = profiles;
		this.configManager = configManager;
		this.globalSettingsSupplier = globalSettingsSupplier;
		this.customPatternsSupplier = customPatternsSupplier;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		Skill[] skills = SkillSelection.getSelectableSkills().toArray(new Skill[0]);
		skillComboBox = new JComboBox<>(skills);
		skillComboBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
			{
				super.getListCellRendererComponent(
					list,
					value,
					index,
					isSelected,
					cellHasFocus
				);
				setText(value instanceof Skill ? ((Skill) value).getName() : "");
				return this;
			}
		});
		PanelUi.setFixedWidth(skillComboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		selectedSkill = skills[0];

		XpFeedbackSettings global = globalSettingsSupplier.get();
		minimumXpSpinner = new JSpinner(new SpinnerNumberModel(
			global.getMinimumXpGain(),
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1
		));
		PanelUi.setFixedWidth(minimumXpSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		intensitySlider = new JSlider(
			XpFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			XpFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			global.getIntensityPercent()
		);
		patternComboBox = PanelUi.createPatternComboBox(customPatternsSupplier);
		durationSpinner = new JSpinner(new SpinnerNumberModel(
			global.getDurationMillis(),
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50
		));
		PanelUi.setFixedWidth(durationSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		intensityValueLabel.setText(global.getIntensityPercent() + "%");

		JPanel skillRow = new JPanel(new BorderLayout(8, 0));
		skillRow.add(new JLabel("Skill"), BorderLayout.CENTER);
		skillRow.add(skillComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, skillRow);
		PanelUi.addVerticalComponent(this, useGlobalCheckBox);
		add(Box.createVerticalStrut(6));

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		thresholdRow.add(minimumXpSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, thresholdRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, intensityHeader);
		PanelUi.addVerticalComponent(this, intensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(patternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(durationSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, durationRow);

		JPanel testRow = new JPanel(new BorderLayout());
		testButton.setToolTipText("Preview the selected skill's effective XP settings");
		testRow.add(testButton, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, testRow);

		skillComboBox.addActionListener(event ->
		{
			Skill selected = (Skill) skillComboBox.getSelectedItem();
			if (selected != null)
			{
				selectedSkill = selected;
				loadSelectedProfile();
			}
		});
		useGlobalCheckBox.addActionListener(event -> toggleOverride());
		minimumXpSpinner.addChangeListener(event -> updateSelectedProfile());
		intensitySlider.addChangeListener(event ->
		{
			intensityValueLabel.setText(intensitySlider.getValue() + "%");
			if (!intensitySlider.getValueIsAdjusting())
			{
				updateSelectedProfile();
			}
		});
		patternComboBox.addActionListener(event ->
		{
			updateSelectedProfile();
			updateControlState();
		});
		durationSpinner.addChangeListener(event -> updateSelectedProfile());
		testButton.addActionListener(event -> testAction.run());
		loadSelectedProfile();
		setConnected(false);
	}

	XpFeedbackSettings getSettings(Skill skill)
	{
		return profiles.resolve(skill, globalSettingsSupplier.get());
	}

	Skill getSelectedSkill()
	{
		return selectedSkill;
	}

	void refreshInheritedProfile()
	{
		if (selectedSkill != null && !profiles.getOverride(selectedSkill).isPresent())
		{
			loadSelectedProfile();
		}
	}

	void applyCustomPatternLibrary(CustomPatternLibrary library)
	{
		SkillFeedbackProfiles resolved = profiles.replaceMissingCustomPatterns(library);
		if (resolved != profiles)
		{
			profiles = resolved;
			persist();
		}
		updatingPatternChoices = true;
		try
		{
			PanelUi.setPatternChoices(
				patternComboBox,
				getSettings(selectedSkill).getPatternSelection(),
				library
			);
		}
		finally
		{
			updatingPatternChoices = false;
		}
		loadSelectedProfile();
	}

	void setConnected(boolean connected)
	{
		this.connected = connected;
		testButton.setEnabled(connected);
	}

	private void toggleOverride()
	{
		if (updatingControls || selectedSkill == null)
		{
			return;
		}
		if (useGlobalCheckBox.isSelected())
		{
			profiles = profiles.withoutOverride(selectedSkill);
		}
		else
		{
			profiles = profiles.withOverride(selectedSkill, globalSettingsSupplier.get());
		}
		persist();
		loadSelectedProfile();
	}

	private void loadSelectedProfile()
	{
		if (selectedSkill == null)
		{
			return;
		}
		XpFeedbackSettings override = profiles.getOverride(selectedSkill).orElse(null);
		XpFeedbackSettings displayed = override == null
			? globalSettingsSupplier.get()
			: override;

		updatingControls = true;
		try
		{
			useGlobalCheckBox.setSelected(override == null);
			minimumXpSpinner.setValue(displayed.getMinimumXpGain());
			intensitySlider.setValue(displayed.getIntensityPercent());
			intensityValueLabel.setText(displayed.getIntensityPercent() + "%");
			patternComboBox.setSelectedItem(displayed.getPatternSelection());
			durationSpinner.setValue(displayed.getDurationMillis());
		}
		finally
		{
			updatingControls = false;
		}
		updateControlState();
	}

	private void updateSelectedProfile()
	{
		if (updatingControls
			|| updatingPatternChoices
			|| selectedSkill == null
			|| useGlobalCheckBox.isSelected())
		{
			return;
		}
		HapticPatternSelection pattern =
			(HapticPatternSelection) patternComboBox.getSelectedItem();
		if (pattern == null)
		{
			return;
		}
		profiles = profiles.withOverride(
			selectedSkill,
			new XpFeedbackSettings(
				((Number) minimumXpSpinner.getValue()).intValue(),
				intensitySlider.getValue(),
				((Number) durationSpinner.getValue()).intValue(),
				pattern
			)
		);
		persist();
	}

	private void updateControlState()
	{
		boolean overridden = !useGlobalCheckBox.isSelected();
		HapticPatternSelection pattern =
			(HapticPatternSelection) patternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();
		minimumXpSpinner.setEnabled(overridden);
		patternComboBox.setEnabled(overridden);
		intensitySlider.setEnabled(overridden && externallyScaled);
		intensityValueLabel.setEnabled(overridden && externallyScaled);
		durationSpinner.setEnabled(overridden && externallyScaled);
		testButton.setEnabled(connected);
	}

	private void persist()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.SKILL_FEEDBACK_PROFILES_KEY,
			profiles.toConfigValue()
		);
	}
}
