package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPattern;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.NotificationFeedbackSettings;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.ConnectionState;
import com.ashy0019.hapticscape.device.DeviceInfo;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
	private static final int NUMERIC_CONTROL_WIDTH = 96;
	private static final int SELECTOR_CONTROL_WIDTH = 108;
	private static final String DURATION_LABEL = "Duration (ms)";

	private final JLabel statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);
	private final DefaultListModel<DeviceInfo> deviceModel = new DefaultListModel<>();
	private final JButton connectButton = new JButton("Connect");
	private final JButton disconnectButton = new JButton("Disconnect");
	private final JButton testButton = new JButton("Test pattern");
	private final JButton testLevelUpButton = new JButton("Test level-up");
	private final JButton testSkillProfileButton = new JButton("Test selected skill");
	private final JButton testNotificationButton = new JButton("Preview notification");
	private final JButton stopButton = new JButton("Stop now");
	private final JCheckBox levelUpFeedbackCheckBox = new JCheckBox("Level-ups");
	private final JCheckBox milestoneFeedbackCheckBox = new JCheckBox("Milestones");
	private final JCheckBox notificationFeedbackCheckBox =
		new JCheckBox("RuneLite notifications");
	private final JCheckBox notificationRespectFocusCheckBox =
		new JCheckBox("Respect RuneLite focus");
	private final JLabel intensityValueLabel = new JLabel();
	private final JLabel skillProfileIntensityValueLabel = new JLabel();
	private final JLabel notificationIntensityValueLabel = new JLabel();
	private final JLabel enabledSkillsValueLabel = new JLabel();
	private final Map<Skill, JCheckBox> skillCheckBoxes = new EnumMap<>(Skill.class);
	private final ConfigManager configManager;
	private final JSlider intensitySlider;
	private final JComboBox<HapticPatternSelection> patternPresetComboBox;
	private final JComboBox<HapticPatternSelection> levelUpPatternPresetComboBox;
	private final JComboBox<HapticPatternSelection> milestonePatternPresetComboBox;
	private final JComboBox<Skill> skillProfileSkillComboBox;
	private final JCheckBox useGlobalSkillSettingsCheckBox =
		new JCheckBox("Use global XP settings");
	private final JComboBox<HapticPatternSelection> skillProfilePatternPresetComboBox;
	private final JSpinner minimumXpGainSpinner;
	private final JSpinner pulseDurationSpinner;
	private final JSpinner skillProfileMinimumXpGainSpinner;
	private final JSlider skillProfileIntensitySlider;
	private final JSpinner skillProfileDurationSpinner;
	private final JSlider notificationIntensitySlider;
	private final JComboBox<HapticPatternSelection> notificationPatternPresetComboBox;
	private final JSpinner notificationDurationSpinner;
	private final PatternForgePanel patternForgePanel;
	private volatile int intensityPercent;
	private volatile int minimumXpGain;
	private volatile int pulseDurationMillis;
	private volatile HapticPatternSelection patternPreset;
	private volatile HapticPatternSelection levelUpPatternPreset;
	private volatile HapticPatternSelection milestonePatternPreset;
	private volatile boolean levelUpFeedbackEnabled;
	private volatile boolean milestoneFeedbackEnabled;
	private volatile SkillSelection skillSelection;
	private volatile SkillFeedbackProfiles skillFeedbackProfiles;
	private volatile Skill selectedProfileSkill;
	private volatile boolean notificationFeedbackEnabled;
	private volatile boolean notificationRespectFocus;
	private volatile int notificationIntensityPercent;
	private volatile int notificationDurationMillis;
	private volatile HapticPatternSelection notificationPatternPreset;
	private volatile CustomPatternLibrary customPatterns;
	private boolean updatingSkillCheckBoxes;
	private boolean updatingSkillProfileControls;
	private boolean updatingPatternSelectors;

	public HapticScapePanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Runnable connectAction,
		Runnable disconnectAction,
		Runnable testAction,
		Runnable testLevelUpAction,
		Runnable testSkillProfileAction,
		Runnable testNotificationAction,
		Consumer<CustomPattern> patternForgePreviewAction,
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
		customPatterns = CustomPatternLibrary.fromConfigValue(config.customPatterns());
		patternPreset = HapticPatternSelection.fromConfigValue(config.patternPreset())
			.resolveAgainst(customPatterns);
		levelUpPatternPreset = HapticPatternSelection
			.fromConfigValue(config.levelUpPatternPreset())
			.resolveAgainst(customPatterns);
		milestonePatternPreset = HapticPatternSelection
			.fromConfigValue(config.milestonePatternPreset())
			.resolveAgainst(customPatterns);
		levelUpFeedbackEnabled = config.levelUpFeedbackEnabled();
		milestoneFeedbackEnabled = config.milestoneFeedbackEnabled();
		skillSelection = SkillSelection.fromConfigValue(config.disabledSkills());
		skillFeedbackProfiles = SkillFeedbackProfiles.fromConfigValue(config.skillFeedbackProfiles())
			.replaceMissingCustomPatterns(customPatterns);
		notificationFeedbackEnabled = config.notificationFeedbackEnabled();
		notificationRespectFocus = config.notificationRespectFocus();
		notificationIntensityPercent = clamp(
			config.notificationIntensityPercent(),
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT
		);
		notificationDurationMillis = clamp(
			config.notificationDurationMillis(),
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS
		);
		notificationPatternPreset = HapticPatternSelection.fromConfigValue(
			config.notificationPatternPreset()
		).resolveAgainst(customPatterns);

		intensitySlider = new JSlider(0, 100, intensityPercent);
		patternPresetComboBox = createPatternComboBox();
		patternPresetComboBox.setSelectedItem(patternPreset);
		patternPresetComboBox.setToolTipText("Choose the pulse sequence used for XP feedback");
		levelUpPatternPresetComboBox = createPatternComboBox();
		levelUpPatternPresetComboBox.setSelectedItem(levelUpPatternPreset);
		levelUpPatternPresetComboBox.setToolTipText("Choose the pattern used for ordinary level-ups");
		levelUpFeedbackCheckBox.setSelected(levelUpFeedbackEnabled);
		levelUpFeedbackCheckBox.setToolTipText("Replace ordinary XP feedback when a real skill level increases");
		milestoneFeedbackCheckBox.setSelected(milestoneFeedbackEnabled);
		milestoneFeedbackCheckBox.setToolTipText(
			"Use distinct feedback for levels 10–90 and level 99"
		);
		milestonePatternPresetComboBox = createPatternComboBox();
		milestonePatternPresetComboBox.setSelectedItem(milestonePatternPreset);
		milestonePatternPresetComboBox.setToolTipText(
			"Choose the pattern used for levels 10–90; level 99 remains Ascending"
		);
		testLevelUpButton.setToolTipText("Preview the configured ordinary level-up pattern");
		testSkillProfileButton.setToolTipText("Preview the selected skill's effective XP settings");
		minimumXpGainSpinner = new JSpinner(new SpinnerNumberModel(
			minimumXpGain,
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1));
		setFixedWidth(minimumXpGainSpinner, NUMERIC_CONTROL_WIDTH);
		pulseDurationSpinner = new JSpinner(new SpinnerNumberModel(
			pulseDurationMillis,
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50));
		setFixedWidth(pulseDurationSpinner, NUMERIC_CONTROL_WIDTH);
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
		setFixedWidth(skillProfileSkillComboBox, SELECTOR_CONTROL_WIDTH);
		selectedProfileSkill = selectableSkills[0];
		skillProfilePatternPresetComboBox = createPatternComboBox();
		skillProfileMinimumXpGainSpinner = new JSpinner(new SpinnerNumberModel(
			minimumXpGain,
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1));
		setFixedWidth(skillProfileMinimumXpGainSpinner, NUMERIC_CONTROL_WIDTH);
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
		setFixedWidth(skillProfileDurationSpinner, NUMERIC_CONTROL_WIDTH);
		skillProfileIntensityValueLabel.setText(intensityPercent + "%");

		notificationFeedbackCheckBox.setSelected(notificationFeedbackEnabled);
		notificationFeedbackCheckBox.setToolTipText(
			"Play haptic feedback when RuneLite or another plugin fires a notification"
		);
		notificationRespectFocusCheckBox.setSelected(notificationRespectFocus);
		notificationRespectFocusCheckBox.setToolTipText(
			"Suppress haptics while RuneLite is focused when the notification would also be suppressed"
		);
		notificationIntensitySlider = new JSlider(
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			notificationIntensityPercent
		);
		notificationIntensityValueLabel.setText(notificationIntensityPercent + "%");
		notificationPatternPresetComboBox = createPatternComboBox();
		notificationPatternPresetComboBox.setSelectedItem(notificationPatternPreset);
		notificationDurationSpinner = new JSpinner(new SpinnerNumberModel(
			notificationDurationMillis,
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50));
		setFixedWidth(notificationDurationSpinner, NUMERIC_CONTROL_WIDTH);
		testNotificationButton.setToolTipText("Preview the configured notification pattern");

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
			if (updatingPatternSelectors)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) patternPresetComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			patternPreset = selected;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.PATTERN_PRESET_KEY,
				selected.toConfigValue());
			refreshInheritedSkillProfile();
		});
		levelUpPatternPresetComboBox.addActionListener(event ->
		{
			if (updatingPatternSelectors)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) levelUpPatternPresetComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			levelUpPatternPreset = selected;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY,
				selected.toConfigValue());
		});
		milestonePatternPresetComboBox.addActionListener(event ->
		{
			if (updatingPatternSelectors)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) milestonePatternPresetComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			milestonePatternPreset = selected;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MILESTONE_PATTERN_PRESET_KEY,
				selected.toConfigValue()
			);
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

		notificationFeedbackCheckBox.addActionListener(event ->
		{
			notificationFeedbackEnabled = notificationFeedbackCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_FEEDBACK_ENABLED_KEY,
				notificationFeedbackEnabled
			);
		});
		notificationRespectFocusCheckBox.addActionListener(event ->
		{
			notificationRespectFocus = notificationRespectFocusCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_RESPECT_FOCUS_KEY,
				notificationRespectFocus
			);
		});
		notificationIntensitySlider.addChangeListener(event ->
		{
			notificationIntensityPercent = notificationIntensitySlider.getValue();
			notificationIntensityValueLabel.setText(notificationIntensityPercent + "%");
			if (!notificationIntensitySlider.getValueIsAdjusting())
			{
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.NOTIFICATION_INTENSITY_PERCENT_KEY,
					notificationIntensityPercent
				);
			}
		});
		notificationPatternPresetComboBox.addActionListener(event ->
		{
			if (updatingPatternSelectors)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) notificationPatternPresetComboBox.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			notificationPatternPreset = selected;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_PATTERN_PRESET_KEY,
				selected.toConfigValue()
			);
		});
		notificationDurationSpinner.addChangeListener(event ->
		{
			notificationDurationMillis =
				((Number) notificationDurationSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_DURATION_MILLIS_KEY,
				notificationDurationMillis
			);
		});

		JPanel settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Feedback"));

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		thresholdRow.add(minimumXpGainSpinner, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, thresholdRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, intensityHeader);
		addVerticalComponent(settingsPanel, intensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(patternPresetComboBox, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(pulseDurationSpinner, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, durationRow);

		JPanel levelUpRow = new JPanel(new BorderLayout(8, 0));
		levelUpRow.add(levelUpFeedbackCheckBox, BorderLayout.CENTER);
		levelUpRow.add(levelUpPatternPresetComboBox, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, levelUpRow);

		JPanel levelUpTestRow = new JPanel(new BorderLayout());
		levelUpTestRow.add(testLevelUpButton, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, levelUpTestRow);

		JPanel milestoneRow = new JPanel(new BorderLayout(8, 0));
		milestoneRow.add(milestoneFeedbackCheckBox, BorderLayout.CENTER);
		milestoneRow.add(milestonePatternPresetComboBox, BorderLayout.EAST);
		addVerticalComponent(settingsPanel, milestoneRow);

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
		addVerticalComponent(skillProfilePanel, profileSkillRow);
		addVerticalComponent(skillProfilePanel, useGlobalSkillSettingsCheckBox);
		skillProfilePanel.add(Box.createVerticalStrut(6));

		JPanel profileThresholdRow = new JPanel(new BorderLayout(8, 0));
		profileThresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		profileThresholdRow.add(skillProfileMinimumXpGainSpinner, BorderLayout.EAST);
		addVerticalComponent(skillProfilePanel, profileThresholdRow);

		JPanel profileIntensityHeader = new JPanel(new BorderLayout());
		profileIntensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		profileIntensityHeader.add(skillProfileIntensityValueLabel, BorderLayout.EAST);
		addVerticalComponent(skillProfilePanel, profileIntensityHeader);
		addVerticalComponent(skillProfilePanel, skillProfileIntensitySlider);

		JPanel profilePatternRow = new JPanel(new BorderLayout(8, 0));
		profilePatternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		profilePatternRow.add(skillProfilePatternPresetComboBox, BorderLayout.EAST);
		addVerticalComponent(skillProfilePanel, profilePatternRow);

		JPanel profileDurationRow = new JPanel(new BorderLayout(8, 0));
		profileDurationRow.add(new JLabel(DURATION_LABEL), BorderLayout.CENTER);
		profileDurationRow.add(skillProfileDurationSpinner, BorderLayout.EAST);
		addVerticalComponent(skillProfilePanel, profileDurationRow);

		JPanel profileTestRow = new JPanel(new BorderLayout());
		profileTestRow.add(testSkillProfileButton, BorderLayout.EAST);
		addVerticalComponent(skillProfilePanel, profileTestRow);

		JPanel notificationPanel = new JPanel();
		notificationPanel.setLayout(new BoxLayout(notificationPanel, BoxLayout.Y_AXIS));
		notificationPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		addVerticalComponent(notificationPanel, notificationFeedbackCheckBox);
		addVerticalComponent(notificationPanel, notificationRespectFocusCheckBox);
		notificationPanel.add(Box.createVerticalStrut(6));

		JPanel notificationIntensityHeader = new JPanel(new BorderLayout());
		notificationIntensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		notificationIntensityHeader.add(notificationIntensityValueLabel, BorderLayout.EAST);
		addVerticalComponent(notificationPanel, notificationIntensityHeader);
		addVerticalComponent(notificationPanel, notificationIntensitySlider);

		JPanel notificationPatternRow = new JPanel(new BorderLayout(8, 0));
		notificationPatternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		notificationPatternRow.add(notificationPatternPresetComboBox, BorderLayout.EAST);
		addVerticalComponent(notificationPanel, notificationPatternRow);

		JPanel notificationDurationRow = new JPanel(new BorderLayout(8, 0));
		notificationDurationRow.add(new JLabel(DURATION_LABEL), BorderLayout.CENTER);
		notificationDurationRow.add(notificationDurationSpinner, BorderLayout.EAST);
		addVerticalComponent(notificationPanel, notificationDurationRow);

		JPanel notificationTestRow = new JPanel(new BorderLayout());
		notificationTestRow.add(testNotificationButton, BorderLayout.EAST);
		addVerticalComponent(notificationPanel, notificationTestRow);

		JTabbedPane feedbackTabs = new JTabbedPane(
			JTabbedPane.TOP,
			JTabbedPane.WRAP_TAB_LAYOUT
		);
		feedbackTabs.putClientProperty(
			"FlatLaf.style",
			"tabInsets: 2,3,2,3; tabHeight: 22"
		);
		addCompactTab(feedbackTabs, "Skills", enabledSkillsPanel);
		addCompactTab(feedbackTabs, "Profiles", skillProfilePanel);
		addCompactTab(feedbackTabs, "Alerts", notificationPanel);
		patternForgePanel = new PatternForgePanel(
			customPatterns,
			configManager,
			this::getPulseDurationMillis,
			patternForgePreviewAction,
			this::applyCustomPatternLibrary
		);
		addCompactTab(feedbackTabs, "Custom", patternForgePanel);
		loadSelectedSkillProfile();

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		addVerticalComponent(topPanel, statusLabel);
		addVerticalComponent(topPanel, settingsPanel);
		addVerticalComponent(topPanel, feedbackTabs);
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
		testNotificationButton.addActionListener(event -> testNotificationAction.run());
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

	public HapticPatternSelection getPatternPreset()
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

	public HapticPatternSelection getLevelUpPatternPreset()
	{
		return levelUpPatternPreset;
	}

	public HapticPatternSelection getMilestonePatternPreset()
	{
		return milestonePatternPreset;
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

	public CustomPatternLibrary getCustomPatterns()
	{
		return customPatterns;
	}

	public NotificationFeedbackSettings getNotificationFeedbackSettings()
	{
		return new NotificationFeedbackSettings(
			notificationFeedbackEnabled,
			notificationRespectFocus,
			notificationIntensityPercent,
			notificationDurationMillis,
			notificationPatternPreset
		);
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
			skillProfilePatternPresetComboBox.setSelectedItem(displayed.getPatternSelection());
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
			|| updatingPatternSelectors
			|| selectedProfileSkill == null
			|| useGlobalSkillSettingsCheckBox.isSelected())
		{
			return;
		}

		HapticPatternSelection selectedPattern =
			(HapticPatternSelection) skillProfilePatternPresetComboBox.getSelectedItem();
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
		int enabledCount = skillSelection.getEnabledCount();
		int skillCount = SkillSelection.getSelectableSkills().size();
		enabledSkillsValueLabel.setText(enabledCount + "/" + skillCount);
		enabledSkillsValueLabel.setToolTipText(
			enabledCount + " of " + skillCount + " skills enabled"
		);
	}

	private JComboBox<HapticPatternSelection> createPatternComboBox()
	{
		HapticPatternSelection[] choices = HapticPatternSelection
			.availableSelections(customPatterns)
			.toArray(new HapticPatternSelection[0]);
		JComboBox<HapticPatternSelection> comboBox = new JComboBox<>(choices);
		comboBox.setRenderer(new DefaultListCellRenderer()
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
				setText(value instanceof HapticPatternSelection
					? ((HapticPatternSelection) value).getDisplayName(customPatterns)
					: "");
				return this;
			}
		});
		setFixedWidth(comboBox, SELECTOR_CONTROL_WIDTH);
		return comboBox;
	}

	private static void setFixedWidth(JComponent component, int width)
	{
		Dimension preferredSize = component.getPreferredSize();
		Dimension fixedSize = new Dimension(width, preferredSize.height);
		component.setPreferredSize(fixedSize);
		component.setMinimumSize(fixedSize);
		component.setMaximumSize(fixedSize);
	}

	private static void addVerticalComponent(JPanel panel, JComponent component)
	{
		Dimension preferredSize = component.getPreferredSize();
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
		panel.add(component);
	}

	private static void addCompactTab(JTabbedPane tabs, String title, Component component)
	{
		tabs.addTab(title, component);
		int tabIndex = tabs.getTabCount() - 1;
		JLabel label = new JLabel(title, SwingConstants.CENTER);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
		label.setPreferredSize(new Dimension(48, 18));
		tabs.setTabComponentAt(tabIndex, label);
	}

	private void applyCustomPatternLibrary(CustomPatternLibrary updatedLibrary)
	{
		customPatterns = updatedLibrary;

		HapticPatternSelection resolvedPattern = patternPreset.resolveAgainst(customPatterns);
		if (!resolvedPattern.equals(patternPreset))
		{
			patternPreset = resolvedPattern;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.PATTERN_PRESET_KEY,
				patternPreset.toConfigValue()
			);
		}

		HapticPatternSelection resolvedLevelUp =
			levelUpPatternPreset.resolveAgainst(customPatterns);
		if (!resolvedLevelUp.equals(levelUpPatternPreset))
		{
			levelUpPatternPreset = resolvedLevelUp;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY,
				levelUpPatternPreset.toConfigValue()
			);
		}

		HapticPatternSelection resolvedMilestone =
			milestonePatternPreset.resolveAgainst(customPatterns);
		if (!resolvedMilestone.equals(milestonePatternPreset))
		{
			milestonePatternPreset = resolvedMilestone;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MILESTONE_PATTERN_PRESET_KEY,
				milestonePatternPreset.toConfigValue()
			);
		}

		HapticPatternSelection resolvedNotification =
			notificationPatternPreset.resolveAgainst(customPatterns);
		if (!resolvedNotification.equals(notificationPatternPreset))
		{
			notificationPatternPreset = resolvedNotification;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_PATTERN_PRESET_KEY,
				notificationPatternPreset.toConfigValue()
			);
		}

		SkillFeedbackProfiles resolvedProfiles =
			skillFeedbackProfiles.replaceMissingCustomPatterns(customPatterns);
		if (resolvedProfiles != skillFeedbackProfiles)
		{
			skillFeedbackProfiles = resolvedProfiles;
			persistSkillFeedbackProfiles();
		}

		refreshPatternSelectors();
	}

	private void refreshPatternSelectors()
	{
		updatingPatternSelectors = true;
		try
		{
			setPatternChoices(patternPresetComboBox, patternPreset);
			setPatternChoices(levelUpPatternPresetComboBox, levelUpPatternPreset);
			setPatternChoices(
				milestonePatternPresetComboBox,
				milestonePatternPreset
			);
			setPatternChoices(
				notificationPatternPresetComboBox,
				notificationPatternPreset
			);
			XpFeedbackSettings selectedSkillSettings = selectedProfileSkill == null
				? getGlobalXpFeedbackSettings()
				: skillFeedbackProfiles.resolve(
					selectedProfileSkill,
					getGlobalXpFeedbackSettings()
				);
			setPatternChoices(
				skillProfilePatternPresetComboBox,
				selectedSkillSettings.getPatternSelection()
			);
		}
		finally
		{
			updatingPatternSelectors = false;
		}
		loadSelectedSkillProfile();
	}

	private void setPatternChoices(
		JComboBox<HapticPatternSelection> comboBox,
		HapticPatternSelection selected)
	{
		HapticPatternSelection[] choices = HapticPatternSelection
			.availableSelections(customPatterns)
			.toArray(new HapticPatternSelection[0]);
		comboBox.setModel(new DefaultComboBoxModel<>(choices));
		comboBox.setSelectedItem(selected.resolveAgainst(customPatterns));
		comboBox.repaint();
	}

	public void close()
	{
		patternForgePanel.close();
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
		testNotificationButton.setEnabled(connected);
		patternForgePanel.setConnected(connected);
		stopButton.setEnabled(connected);
	}
}
