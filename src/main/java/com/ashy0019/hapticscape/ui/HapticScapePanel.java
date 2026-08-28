package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.HapticPatternPreset;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.ConnectionState;
import com.ashy0019.hapticscape.device.DeviceInfo;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public final class HapticScapePanel extends PluginPanel
{
	private final JLabel statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);
	private final DefaultListModel<DeviceInfo> deviceModel = new DefaultListModel<>();
	private final JButton connectButton = new JButton("Connect");
	private final JButton disconnectButton = new JButton("Disconnect");
	private final JButton testButton = new JButton("Test pattern");
	private final JButton testLevelUpButton = new JButton("Test level");
	private final JButton testSkillProfileButton = new JButton("Test selected skill");
	private final JButton stopButton = new JButton("Stop now");
	private final JCheckBox levelUpFeedbackCheckBox = new JCheckBox("Level-ups");
	private final JCheckBox milestoneFeedbackCheckBox = new JCheckBox("Milestones");
	private final JLabel intensityValueLabel = new JLabel();
	private final JLabel skillProfileIntensityValueLabel = new JLabel();
	private final JLabel enabledSkillsValueLabel = new JLabel();
	private final Map<Skill, JCheckBox> skillCheckBoxes = new EnumMap<>(Skill.class);
	private final ConfigManager configManager;
	private final JSlider intensitySlider;
	private final JComboBox<HapticPatternPreset> patternPresetComboBox;
	private final JComboBox<HapticPatternPreset> levelUpPatternPresetComboBox;
	private final JComboBox<Skill> skillProfileSkillComboBox;
	private final JCheckBox useGlobalSkillSettingsCheckBox =
		new JCheckBox("Use global XP settings");
	private final JComboBox<HapticPatternPreset> skillProfilePatternPresetComboBox;
	private final JSpinner minimumXpGainSpinner;
	private final JSpinner pulseDurationSpinner;
	private final JSpinner skillProfileMinimumXpGainSpinner;
	private final JSlider skillProfileIntensitySlider;
	private final JSpinner skillProfileDurationSpinner;
	private volatile int intensityPercent;
	private volatile int minimumXpGain;
	private volatile int pulseDurationMillis;
	private volatile HapticPatternPreset patternPreset;
	private volatile HapticPatternPreset levelUpPatternPreset;
	private volatile boolean levelUpFeedbackEnabled;
	private volatile boolean milestoneFeedbackEnabled;
	private volatile SkillSelection skillSelection;
	private volatile SkillFeedbackProfiles skillFeedbackProfiles;
	private volatile Skill selectedProfileSkill;
	private boolean updatingSkillCheckBoxes;
	private boolean updatingSkillProfileControls;

	public HapticScapePanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Runnable connectAction,
		Runnable disconnectAction,
		Runnable testAction,
		Runnable testLevelUpAction,
		Runnable testSkillProfileAction,
		Runnable stopAction)
	{
		super(false);
		this.configManager = configManager;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

		intensityPercent = clamp(config.intensityPercent(), 0, 100);
		minimumXpGain = clamp(
			config.minimumXpGain(),
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN
		);
		pulseDurationMillis = clamp(
			config.pulseDurationMillis(),
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS
		);
		patternPreset = HapticPatternPreset.fromConfigValue(config.patternPreset());
		levelUpPatternPreset = HapticPatternPreset.fromConfigValue(config.levelUpPatternPreset());
		levelUpFeedbackEnabled = config.levelUpFeedbackEnabled();
		milestoneFeedbackEnabled = config.milestoneFeedbackEnabled();
		skillSelection = SkillSelection.fromConfigValue(config.disabledSkills());
		skillFeedbackProfiles = SkillFeedbackProfiles.fromConfigValue(config.skillFeedbackProfiles());

		intensitySlider = new JSlider(0, 100, intensityPercent);
		patternPresetComboBox = new JComboBox<>(HapticPatternPreset.values());
		patternPresetComboBox.setSelectedItem(patternPreset);
		patternPresetComboBox.setToolTipText("Choose the pulse sequence used for XP feedback");
		levelUpPatternPresetComboBox = new JComboBox<>(HapticPatternPreset.values());
		levelUpPatternPresetComboBox.setSelectedItem(levelUpPatternPreset);
		levelUpPatternPresetComboBox.setToolTipText("Choose the pattern used for ordinary level-ups");
		levelUpFeedbackCheckBox.setSelected(levelUpFeedbackEnabled);
		levelUpFeedbackCheckBox.setToolTipText("Replace ordinary XP feedback when a real skill level increases");
		milestoneFeedbackCheckBox.setSelected(milestoneFeedbackEnabled);
		milestoneFeedbackCheckBox.setToolTipText("Use Triple pulse for levels 10–90 and Ascending for level 99");
		testLevelUpButton.setToolTipText("Preview the configured ordinary level-up pattern");
		testSkillProfileButton.setToolTipText("Preview the selected skill's effective XP settings");
		minimumXpGainSpinner = new JSpinner(new SpinnerNumberModel(
			minimumXpGain,
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1));
		pulseDurationSpinner = new JSpinner(new SpinnerNumberModel(
			pulseDurationMillis,
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50));
		pulseDurationSpinner.setToolTipText("Total time shared by all pulses and gaps in the pattern");

		Skill[] selectableSkills = SkillSelection.getSelectableSkills().toArray(new Skill[0]);
		skillProfileSkillComboBox = new JComboBox<>(selectableSkills);
		skillProfileSkillComboBox.setRenderer(new DefaultListCellRenderer()
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
		selectedProfileSkill = selectableSkills[0];
		skillProfilePatternPresetComboBox = new JComboBox<>(HapticPatternPreset.values());
		skillProfileMinimumXpGainSpinner = new JSpinner(new SpinnerNumberModel(
			minimumXpGain,
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1));
		skillProfileIntensitySlider = new JSlider(
			XpFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			XpFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			intensityPercent
		);
		skillProfileDurationSpinner = new JSpinner(new SpinnerNumberModel(
			pulseDurationMillis,
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50));
		skillProfileIntensityValueLabel.setText(intensityPercent + "%");

		intensityValueLabel.setText(intensitySlider.getValue() + "%");
		intensitySlider.addChangeListener(event ->
		{
			intensityPercent = intensitySlider.getValue();
			intensityValueLabel.setText(intensityPercent + "%");
			refreshInheritedSkillProfile();
			if (!intensitySlider.getValueIsAdjusting())
			{
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.INTENSITY_PERCENT_KEY,
					intensityPercent);
			}
		});
		minimumXpGainSpinner.addChangeListener(event ->
		{
			minimumXpGain = ((Number) minimumXpGainSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MINIMUM_XP_GAIN_KEY,
				minimumXpGain);
			refreshInheritedSkillProfile();
		});
		pulseDurationSpinner.addChangeListener(event ->
		{
			pulseDurationMillis = ((Number) pulseDurationSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.PULSE_DURATION_MILLIS_KEY,
				pulseDurationMillis);
			refreshInheritedSkillProfile();
		});
		patternPresetComboBox.addActionListener(event ->
		{
			HapticPatternPreset selected = (HapticPatternPreset) patternPresetComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			patternPreset = selected;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.PATTERN_PRESET_KEY,
				selected.name());
			refreshInheritedSkillProfile();
		});
		levelUpPatternPresetComboBox.addActionListener(event ->
		{
			HapticPatternPreset selected =
				(HapticPatternPreset) levelUpPatternPresetComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			levelUpPatternPreset = selected;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY,
				selected.name());
		});
		levelUpFeedbackCheckBox.addActionListener(event ->
		{
			levelUpFeedbackEnabled = levelUpFeedbackCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.LEVEL_UP_FEEDBACK_ENABLED_KEY,
				levelUpFeedbackEnabled);
		});
		milestoneFeedbackCheckBox.addActionListener(event ->
		{
			milestoneFeedbackEnabled = milestoneFeedbackCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MILESTONE_FEEDBACK_ENABLED_KEY,
				milestoneFeedbackEnabled);
		});

		skillProfileSkillComboBox.addActionListener(event ->
		{
			Skill selected = (Skill) skillProfileSkillComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			selectedProfileSkill = selected;
			loadSelectedSkillProfile();
		});
		useGlobalSkillSettingsCheckBox.addActionListener(event ->
		{
			if (updatingSkillProfileControls || selectedProfileSkill == null)
			{
				return;
			}

			if (useGlobalSkillSettingsCheckBox.isSelected())
			{
				skillFeedbackProfiles = skillFeedbackProfiles.withoutOverride(selectedProfileSkill);
			}
			else
			{
				skillFeedbackProfiles = skillFeedbackProfiles.withOverride(
					selectedProfileSkill,
					getGlobalXpFeedbackSettings()
				);
			}
			persistSkillFeedbackProfiles();
			loadSelectedSkillProfile();
		});
		skillProfileMinimumXpGainSpinner.addChangeListener(event -> updateSelectedSkillProfile());
		skillProfileIntensitySlider.addChangeListener(event ->
		{
			skillProfileIntensityValueLabel.setText(skillProfileIntensitySlider.getValue() + "%");
			if (!skillProfileIntensitySlider.getValueIsAdjusting())
			{
				updateSelectedSkillProfile();
			}
		});
		skillProfilePatternPresetComboBox.addActionListener(event -> updateSelectedSkillProfile());
		skillProfileDurationSpinner.addChangeListener(event -> updateSelectedSkillProfile());

		JPanel settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Feedback"));

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		thresholdRow.add(minimumXpGainSpinner, BorderLayout.EAST);
		settingsPanel.add(thresholdRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		settingsPanel.add(intensityHeader);
		settingsPanel.add(intensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(patternPresetComboBox, BorderLayout.EAST);
		settingsPanel.add(patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel("Pattern duration (ms)"), BorderLayout.CENTER);
		durationRow.add(pulseDurationSpinner, BorderLayout.EAST);
		settingsPanel.add(durationRow);

		JPanel levelUpRow = new JPanel(new BorderLayout(8, 0));
		levelUpRow.add(levelUpFeedbackCheckBox, BorderLayout.CENTER);
		levelUpRow.add(levelUpPatternPresetComboBox, BorderLayout.EAST);
		settingsPanel.add(levelUpRow);

		JPanel milestoneRow = new JPanel(new BorderLayout(8, 0));
		milestoneRow.add(milestoneFeedbackCheckBox, BorderLayout.CENTER);
		milestoneRow.add(testLevelUpButton, BorderLayout.EAST);
		settingsPanel.add(milestoneRow);

		JPanel enabledSkillsPanel = new JPanel(new BorderLayout(0, 4));
		enabledSkillsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

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
		enabledSkillsPanel.add(skillsHeader, BorderLayout.NORTH);

		JPanel skillGrid = new JPanel(new GridLayout(0, 2, 4, 2));
		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			JCheckBox skillCheckBox = new JCheckBox(skill.getName(), skillSelection.isEnabled(skill));
			skillCheckBox.addActionListener(event -> setSkillEnabled(skill, skillCheckBox.isSelected()));
			skillCheckBoxes.put(skill, skillCheckBox);
			skillGrid.add(skillCheckBox);
		}

		enabledSkillsPanel.add(skillGrid, BorderLayout.CENTER);
		updateEnabledSkillsLabel();

		JPanel skillProfilePanel = new JPanel();
		skillProfilePanel.setLayout(new BoxLayout(skillProfilePanel, BoxLayout.Y_AXIS));
		skillProfilePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JPanel profileSkillRow = new JPanel(new BorderLayout(8, 0));
		profileSkillRow.add(new JLabel("Skill"), BorderLayout.CENTER);
		profileSkillRow.add(skillProfileSkillComboBox, BorderLayout.EAST);
		skillProfilePanel.add(profileSkillRow);
		skillProfilePanel.add(useGlobalSkillSettingsCheckBox);

		JPanel profileThresholdRow = new JPanel(new BorderLayout(8, 0));
		profileThresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		profileThresholdRow.add(skillProfileMinimumXpGainSpinner, BorderLayout.EAST);
		skillProfilePanel.add(profileThresholdRow);

		JPanel profileIntensityHeader = new JPanel(new BorderLayout());
		profileIntensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		profileIntensityHeader.add(skillProfileIntensityValueLabel, BorderLayout.EAST);
		skillProfilePanel.add(profileIntensityHeader);
		skillProfilePanel.add(skillProfileIntensitySlider);

		JPanel profilePatternRow = new JPanel(new BorderLayout(8, 0));
		profilePatternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		profilePatternRow.add(skillProfilePatternPresetComboBox, BorderLayout.EAST);
		skillProfilePanel.add(profilePatternRow);

		JPanel profileDurationRow = new JPanel(new BorderLayout(8, 0));
		profileDurationRow.add(new JLabel("Pattern duration (ms)"), BorderLayout.CENTER);
		profileDurationRow.add(skillProfileDurationSpinner, BorderLayout.EAST);
		skillProfilePanel.add(profileDurationRow);

		JPanel profileTestRow = new JPanel(new BorderLayout());
		profileTestRow.add(testSkillProfileButton, BorderLayout.EAST);
		skillProfilePanel.add(profileTestRow);

		JTabbedPane skillTabs = new JTabbedPane();
		skillTabs.addTab("Enabled skills", enabledSkillsPanel);
		skillTabs.addTab("Skill profiles", skillProfilePanel);
		loadSelectedSkillProfile();

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(statusLabel);
		topPanel.add(settingsPanel);
		topPanel.add(skillTabs);
		add(topPanel, BorderLayout.NORTH);

		JList<DeviceInfo> deviceList = new JList<>(deviceModel);
		JScrollPane scrollPane = new JScrollPane(deviceList);
		scrollPane.setPreferredSize(new Dimension(0, 140));
		scrollPane.setBorder(BorderFactory.createTitledBorder("Devices"));
		add(scrollPane, BorderLayout.CENTER);

		connectButton.addActionListener(event ->
		{
			connectButton.setEnabled(false);
			statusLabel.setText("Connecting");
			connectAction.run();
		});
		disconnectButton.addActionListener(event -> disconnectAction.run());
		testButton.addActionListener(event -> testAction.run());
		testLevelUpButton.addActionListener(event -> testLevelUpAction.run());
		testSkillProfileButton.addActionListener(event -> testSkillProfileAction.run());
		stopButton.addActionListener(event -> stopAction.run());

		JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
		buttons.add(connectButton);
		buttons.add(disconnectButton);
		buttons.add(testButton);
		buttons.add(stopButton);
		add(buttons, BorderLayout.SOUTH);

		applyState(ConnectionSnapshot.disconnected());
	}

	public int getIntensityPercent()
	{
		return intensityPercent;
	}

	public int getMinimumXpGain()
	{
		return minimumXpGain;
	}

	public int getPulseDurationMillis()
	{
		return pulseDurationMillis;
	}

	public HapticPatternPreset getPatternPreset()
	{
		return patternPreset;
	}

	public XpFeedbackSettings getGlobalXpFeedbackSettings()
	{
		return new XpFeedbackSettings(
			minimumXpGain,
			intensityPercent,
			pulseDurationMillis,
			patternPreset
		);
	}

	public XpFeedbackSettings getXpFeedbackSettings(Skill skill)
	{
		return skillFeedbackProfiles.resolve(skill, getGlobalXpFeedbackSettings());
	}

	public Skill getSelectedProfileSkill()
	{
		return selectedProfileSkill;
	}

	public HapticPatternPreset getLevelUpPatternPreset()
	{
		return levelUpPatternPreset;
	}

	public boolean isLevelUpFeedbackEnabled()
	{
		return levelUpFeedbackEnabled;
	}

	public boolean isMilestoneFeedbackEnabled()
	{
		return milestoneFeedbackEnabled;
	}

	public boolean isSkillEnabled(Skill skill)
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
		persistSkillSelection();
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

		persistSkillSelection();
		updateEnabledSkillsLabel();
	}

	private void persistSkillSelection()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.DISABLED_SKILLS_KEY,
			skillSelection.toConfigValue()
		);
	}

	private void loadSelectedSkillProfile()
	{
		Skill skill = selectedProfileSkill;
		if (skill == null)
		{
			return;
		}

		XpFeedbackSettings override = skillFeedbackProfiles.getOverride(skill).orElse(null);
		XpFeedbackSettings displayed = override == null ? getGlobalXpFeedbackSettings() : override;

		updatingSkillProfileControls = true;
		try
		{
			useGlobalSkillSettingsCheckBox.setSelected(override == null);
			skillProfileMinimumXpGainSpinner.setValue(displayed.getMinimumXpGain());
			skillProfileIntensitySlider.setValue(displayed.getIntensityPercent());
			skillProfileIntensityValueLabel.setText(displayed.getIntensityPercent() + "%");
			skillProfilePatternPresetComboBox.setSelectedItem(displayed.getPatternPreset());
			skillProfileDurationSpinner.setValue(displayed.getDurationMillis());
			setSkillProfileControlsEnabled(override != null);
		}
		finally
		{
			updatingSkillProfileControls = false;
		}
	}

	private void updateSelectedSkillProfile()
	{
		if (updatingSkillProfileControls
			|| selectedProfileSkill == null
			|| useGlobalSkillSettingsCheckBox.isSelected())
		{
			return;
		}

		HapticPatternPreset selectedPattern =
			(HapticPatternPreset) skillProfilePatternPresetComboBox.getSelectedItem();
		if (selectedPattern == null)
		{
			return;
		}

		XpFeedbackSettings updated = new XpFeedbackSettings(
			((Number) skillProfileMinimumXpGainSpinner.getValue()).intValue(),
			skillProfileIntensitySlider.getValue(),
			((Number) skillProfileDurationSpinner.getValue()).intValue(),
			selectedPattern
		);
		skillFeedbackProfiles = skillFeedbackProfiles.withOverride(selectedProfileSkill, updated);
		persistSkillFeedbackProfiles();
	}

	private void refreshInheritedSkillProfile()
	{
		if (selectedProfileSkill != null
			&& !skillFeedbackProfiles.getOverride(selectedProfileSkill).isPresent())
		{
			loadSelectedSkillProfile();
		}
	}

	private void setSkillProfileControlsEnabled(boolean enabled)
	{
		skillProfileMinimumXpGainSpinner.setEnabled(enabled);
		skillProfileIntensitySlider.setEnabled(enabled);
		skillProfileIntensityValueLabel.setEnabled(enabled);
		skillProfilePatternPresetComboBox.setEnabled(enabled);
		skillProfileDurationSpinner.setEnabled(enabled);
	}

	private void persistSkillFeedbackProfiles()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.SKILL_FEEDBACK_PROFILES_KEY,
			skillFeedbackProfiles.toConfigValue()
		);
	}

	private void updateEnabledSkillsLabel()
	{
		enabledSkillsValueLabel.setText(
			skillSelection.getEnabledCount()
				+ "/"
				+ SkillSelection.getSelectableSkills().size()
				+ " enabled"
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	public void updateConnection(ConnectionSnapshot snapshot)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> applyState(snapshot));
			return;
		}
		applyState(snapshot);
	}

	public void showInputError(String message)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> showInputError(message));
			return;
		}
		statusLabel.setText(message);
		connectButton.setEnabled(true);
	}

	private void applyState(ConnectionSnapshot snapshot)
	{
		statusLabel.setText(snapshot.getMessage());

		deviceModel.clear();
		for (DeviceInfo device : snapshot.getDevices())
		{
			deviceModel.addElement(device);
		}

		ConnectionState state = snapshot.getState();
		boolean connected = state == ConnectionState.CONNECTED;
		connectButton.setEnabled(state == ConnectionState.DISCONNECTED);
		disconnectButton.setEnabled(state == ConnectionState.CONNECTING || connected);
		testButton.setEnabled(connected);
		testLevelUpButton.setEnabled(connected);
		testSkillProfileButton.setEnabled(connected);
		stopButton.setEnabled(connected);
	}
}
