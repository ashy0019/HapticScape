package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.AlertProfiles;
import com.ashy0019.hapticscape.AlertTriggerSettings;
import com.ashy0019.hapticscape.CustomPatternEntry;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.NotificationFeedbackSettings;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.ConnectionState;
import com.ashy0019.hapticscape.device.DeviceInfo;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.MusicSyncSnapshot;
import com.ashy0019.hapticscape.update.UpdateCheckService;
import com.ashy0019.hapticscape.update.UpdatePreferencesStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public final class HapticScapePanel extends PluginPanel
{
	private static final int DEVELOPER_UNLOCK_CLICKS = 9;
	private static final long DEVELOPER_UNLOCK_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(4);

	private final ConfigManager configManager;
	private final JLabel statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);
	private final DefaultListModel<DeviceInfo> deviceModel = new DefaultListModel<>();
	private final JButton connectButton = new JButton("Connect");
	private final JButton disconnectButton = new JButton("Disconnect");
	private final JButton testButton = new JButton("Test pattern");
	private final JButton testLevelUpButton = new JButton("Test level-up");
	private final JButton previewLevel99Button = new JButton("Test 99");
	private final JButton stopButton = new JButton("Stop now");
	private final JButton updatesButton = new JButton("Updates");
	private final JCheckBox levelUpCheckBox = new JCheckBox("Level-ups");
	private final JCheckBox milestoneCheckBox = new JCheckBox("Milestones");
	private final JCheckBox level99CheckBox = new JCheckBox("Celebrate level 99");
	private final JLabel intensityValueLabel = new JLabel();
	private final JSlider intensitySlider;
	private final JSpinner minimumXpSpinner;
	private final JSpinner durationSpinner;
	private final JComboBox<HapticPatternSelection> patternComboBox;
	private final JComboBox<HapticPatternSelection> levelUpPatternComboBox;
	private final JComboBox<HapticPatternSelection> milestonePatternComboBox;
	private final JPanel level99Row = new JPanel(new BorderLayout(8, 0));
	private final SkillsPanel skillsPanel;
	private final ProfilesPanel profilesPanel;
	private final AlertsPanel alertsPanel;
	private final CustomPatternsPanel customPatternsPanel;
	private final MusicPanel musicPanel;
	private final ClickerPanel clickerPanel;
	private final UpdatesPanel updatesPanel;
	private final Timer developerStatusTimer;

	private volatile int intensityPercent;
	private volatile int minimumXpGain;
	private volatile int durationMillis;
	private volatile HapticPatternSelection patternSelection;
	private volatile HapticPatternSelection levelUpPatternSelection;
	private volatile HapticPatternSelection milestonePatternSelection;
	private volatile boolean levelUpEnabled;
	private volatile boolean milestoneEnabled;
	private volatile boolean level99Enabled;
	private volatile CustomPatternLibrary customPatterns;
	private boolean updatingPatternSelectors;
	private boolean developerControlsUnlocked;
	private int developerUnlockClickCount;
	private long developerUnlockStartedNanos;
	private ConnectionSnapshot latestConnectionSnapshot = ConnectionSnapshot.disconnected();

	public HapticScapePanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Runnable connectAction,
		Runnable disconnectAction,
		Runnable testAction,
		Runnable testLevelUpAction,
		Runnable previewLevel99Action,
		Runnable testSkillProfileAction,
		Runnable testGenericAlertAction,
		Consumer<AlertCategory> testSpecificAlertAction,
		Consumer<CustomPatternEntry> patternForgePreviewAction,
		Consumer<MusicSyncSettings> musicSettingsAction,
		Consumer<ClickerSettings> clickerSettingsAction,
		Runnable testClickAction,
		UpdatePreferencesStore updatePreferencesStore,
		UpdateCheckService updateCheckService,
		Runnable stopAction)
	{
		super(false);
		this.configManager = configManager;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
		updatesButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
		updatesButton.setToolTipText("Configure HapticScape client updates");
		developerStatusTimer = new Timer(1600, event ->
			statusLabel.setText(latestConnectionSnapshot.getMessage()));
		developerStatusTimer.setRepeats(false);

		intensityPercent = clamp(config.intensityPercent(), 0, 100);
		minimumXpGain = clamp(
			config.minimumXpGain(),
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN
		);
		durationMillis = clamp(
			config.pulseDurationMillis(),
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS
		);
		customPatterns = CustomPatternLibrary.fromConfigValue(config.customPatterns());
		patternSelection = HapticPatternSelection.fromConfigValue(config.patternPreset())
			.resolveAgainst(customPatterns);
		levelUpPatternSelection = HapticPatternSelection
			.fromConfigValue(config.levelUpPatternPreset())
			.resolveAgainst(customPatterns);
		milestonePatternSelection = HapticPatternSelection
			.fromConfigValue(config.milestonePatternPreset())
			.resolveAgainst(customPatterns);
		levelUpEnabled = config.levelUpFeedbackEnabled();
		milestoneEnabled = config.milestoneFeedbackEnabled();
		level99Enabled = config.level99CelebrationEnabled();

		intensitySlider = new JSlider(0, 100, intensityPercent);
		intensityValueLabel.setText(intensityPercent + "%");
		minimumXpSpinner = new JSpinner(new SpinnerNumberModel(
			minimumXpGain,
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1
		));
		PanelUi.setFixedWidth(minimumXpSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		durationSpinner = new JSpinner(new SpinnerNumberModel(
			durationMillis,
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50
		));
		PanelUi.setFixedWidth(durationSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		durationSpinner.setToolTipText(
			"Total time shared by all pulses and gaps in a built-in pattern"
		);

		patternComboBox = PanelUi.createPatternComboBox(() -> customPatterns);
		patternComboBox.setSelectedItem(patternSelection);
		patternComboBox.setToolTipText("Choose the pattern used for XP feedback");
		levelUpPatternComboBox = PanelUi.createPatternComboBox(() -> customPatterns);
		levelUpPatternComboBox.setSelectedItem(levelUpPatternSelection);
		levelUpPatternComboBox.setToolTipText("Choose the pattern used for ordinary level-ups");
		milestonePatternComboBox = PanelUi.createPatternComboBox(() -> customPatterns);
		milestonePatternComboBox.setSelectedItem(milestonePatternSelection);
		milestonePatternComboBox.setToolTipText("Choose the pattern used for levels 10–90");

		levelUpCheckBox.setSelected(levelUpEnabled);
		levelUpCheckBox.setToolTipText(
			"Replace ordinary XP feedback when a real skill level increases"
		);
		milestoneCheckBox.setSelected(milestoneEnabled);
		milestoneCheckBox.setToolTipText("Use distinct feedback for levels 10–90");
		level99CheckBox.setSelected(level99Enabled);
		level99CheckBox.setToolTipText(
			"Celebrate real skill level 99 with a dedicated mastery ceremony"
		);
		testLevelUpButton.setToolTipText("Preview the configured ordinary level-up pattern");
		previewLevel99Button.setToolTipText(
			"Preview the Level 99 ceremony using the skill selected on Profiles"
		);
		previewLevel99Button.setMargin(new java.awt.Insets(2, 6, 2, 6));
		previewLevel99Button.setVisible(false);

		configureGlobalListeners();
		JPanel settingsPanel = createGlobalSettingsPanel();

		skillsPanel = new SkillsPanel(
			SkillSelection.fromConfigValue(config.disabledSkills()),
			configManager
		);
		profilesPanel = new ProfilesPanel(
			SkillFeedbackProfiles.fromConfigValue(config.skillFeedbackProfiles())
				.replaceMissingCustomPatterns(customPatterns),
			configManager,
			this::getGlobalXpFeedbackSettings,
			() -> customPatterns,
			testSkillProfileAction
		);
		alertsPanel = new AlertsPanel(
			config,
			configManager,
			() -> customPatterns,
			testGenericAlertAction,
			testSpecificAlertAction
		);
		customPatternsPanel = new CustomPatternsPanel(
			customPatterns,
			configManager,
			patternForgePreviewAction,
			this::applyCustomPatternLibrary
		);
		musicPanel = new MusicPanel(config, configManager, musicSettingsAction);
		clickerPanel = new ClickerPanel(
			config,
			configManager,
			clickerSettingsAction,
			testClickAction
		);
		updatesPanel = new UpdatesPanel(updatePreferencesStore, updateCheckService);

		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		tabs.putClientProperty(
			"FlatLaf.style",
			"tabInsets: 2,3,2,3; tabHeight: 22; tabAreaAlignment: center"
		);
		PanelUi.addCompactTab(tabs, "Skills", skillsPanel);
		PanelUi.addCompactTab(tabs, "XP", profilesPanel);
		PanelUi.addCompactTab(tabs, "Alerts", alertsPanel);
		PanelUi.addCompactTab(tabs, "Forge", customPatternsPanel);
		PanelUi.addCompactTab(tabs, "Music", musicPanel);
		PanelUi.addCompactTab(tabs, "Click", clickerPanel);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(topPanel, statusLabel);
		PanelUi.addVerticalComponent(topPanel, settingsPanel);
		PanelUi.addVerticalComponent(topPanel, tabs);
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
		previewLevel99Button.addActionListener(event -> previewLevel99Action.run());
		stopButton.addActionListener(event ->
		{
			musicPanel.disableMusicSync();
			stopAction.run();
		});
		updatesButton.addActionListener(event -> JOptionPane.showMessageDialog(
			this,
			updatesPanel,
			"HapticScape updates",
			JOptionPane.PLAIN_MESSAGE));

		JPanel primaryButtons = new JPanel(new GridLayout(2, 2, 6, 6));
		primaryButtons.add(connectButton);
		primaryButtons.add(disconnectButton);
		primaryButtons.add(testButton);
		primaryButtons.add(stopButton);

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(buttons, primaryButtons);
		PanelUi.addVerticalComponent(buttons, updatesButton);
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
		return durationMillis;
	}

	public HapticPatternSelection getPatternPreset()
	{
		return patternSelection;
	}

	public XpFeedbackSettings getGlobalXpFeedbackSettings()
	{
		return new XpFeedbackSettings(
			minimumXpGain,
			intensityPercent,
			durationMillis,
			patternSelection
		);
	}

	public XpFeedbackSettings getXpFeedbackSettings(Skill skill)
	{
		return profilesPanel.getSettings(skill);
	}

	public Skill getSelectedProfileSkill()
	{
		return profilesPanel.getSelectedSkill();
	}

	public HapticPatternSelection getLevelUpPatternPreset()
	{
		return levelUpPatternSelection;
	}

	public HapticPatternSelection getMilestonePatternPreset()
	{
		return milestonePatternSelection;
	}

	public boolean isLevelUpFeedbackEnabled()
	{
		return levelUpEnabled;
	}

	public boolean isMilestoneFeedbackEnabled()
	{
		return milestoneEnabled;
	}

	public boolean isLevel99CelebrationEnabled()
	{
		return level99Enabled;
	}

	public boolean isSkillEnabled(Skill skill)
	{
		return skillsPanel.isSkillEnabled(skill);
	}

	public CustomPatternLibrary getCustomPatterns()
	{
		return customPatterns;
	}

	public NotificationFeedbackSettings getNotificationFeedbackSettings()
	{
		return alertsPanel.getGenericSettings();
	}

	public AlertProfiles getAlertProfiles()
	{
		return alertsPanel.getAlertProfiles();
	}

	public AlertTriggerSettings getAlertTriggerSettings()
	{
		return alertsPanel.getTriggerSettings();
	}

	public MusicSyncSettings getMusicSyncSettings()
	{
		return musicPanel.getSettings();
	}

	public ClickerSettings getClickerSettings()
	{
		return clickerPanel.getSettings();
	}

	public void updateMusicSync(MusicSyncSnapshot snapshot)
	{
		musicPanel.updateSnapshot(snapshot);
	}

	private void configureGlobalListeners()
	{
		intensitySlider.addChangeListener(event ->
		{
			intensityPercent = intensitySlider.getValue();
			intensityValueLabel.setText(intensityPercent + "%");
			refreshInheritedProfileIfReady();
			if (!intensitySlider.getValueIsAdjusting())
			{
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.INTENSITY_PERCENT_KEY,
					intensityPercent
				);
			}
		});
		minimumXpSpinner.addChangeListener(event ->
		{
			minimumXpGain = ((Number) minimumXpSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MINIMUM_XP_GAIN_KEY,
				minimumXpGain
			);
			refreshInheritedProfileIfReady();
		});
		durationSpinner.addChangeListener(event ->
		{
			durationMillis = ((Number) durationSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.PULSE_DURATION_MILLIS_KEY,
				durationMillis
			);
			refreshInheritedProfileIfReady();
		});
		patternComboBox.addActionListener(event ->
		{
			if (updatingPatternSelectors)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) patternComboBox.getSelectedItem();
			if (selected != null)
			{
				patternSelection = selected;
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.PATTERN_PRESET_KEY,
					selected.toConfigValue()
				);
				refreshInheritedProfileIfReady();
			}
		});
		levelUpPatternComboBox.addActionListener(event ->
		{
			if (!updatingPatternSelectors)
			{
				HapticPatternSelection selected =
					(HapticPatternSelection) levelUpPatternComboBox.getSelectedItem();
				if (selected != null)
				{
					levelUpPatternSelection = selected;
					configManager.setConfiguration(
						HapticScapeConfig.GROUP,
						HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY,
						selected.toConfigValue()
					);
				}
			}
		});
		milestonePatternComboBox.addActionListener(event ->
		{
			if (!updatingPatternSelectors)
			{
				HapticPatternSelection selected =
					(HapticPatternSelection) milestonePatternComboBox.getSelectedItem();
				if (selected != null)
				{
					milestonePatternSelection = selected;
					configManager.setConfiguration(
						HapticScapeConfig.GROUP,
						HapticScapeConfig.MILESTONE_PATTERN_PRESET_KEY,
						selected.toConfigValue()
					);
				}
			}
		});
		levelUpCheckBox.addActionListener(event ->
		{
			levelUpEnabled = levelUpCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.LEVEL_UP_FEEDBACK_ENABLED_KEY,
				levelUpEnabled
			);
		});
		milestoneCheckBox.addActionListener(event ->
		{
			milestoneEnabled = milestoneCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MILESTONE_FEEDBACK_ENABLED_KEY,
				milestoneEnabled
			);
		});
		level99CheckBox.addActionListener(event ->
		{
			level99Enabled = level99CheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.LEVEL_99_CELEBRATION_ENABLED_KEY,
				level99Enabled
			);
		});
		statusLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				handleDeveloperUnlockClick(event);
			}
		});
	}

	private JPanel createGlobalSettingsPanel()
	{
		JPanel settings = new JPanel();
		settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
		settings.setBorder(BorderFactory.createTitledBorder("Feedback"));

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		thresholdRow.add(minimumXpSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, thresholdRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, intensityHeader);
		PanelUi.addVerticalComponent(settings, intensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(patternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(durationSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, durationRow);

		JPanel levelUpRow = new JPanel(new BorderLayout(8, 0));
		levelUpRow.add(levelUpCheckBox, BorderLayout.CENTER);
		levelUpRow.add(levelUpPatternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, levelUpRow);

		JPanel levelUpTestRow = new JPanel(new BorderLayout());
		levelUpTestRow.add(testLevelUpButton, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, levelUpTestRow);

		JPanel milestoneRow = new JPanel(new BorderLayout(8, 0));
		milestoneRow.add(milestoneCheckBox, BorderLayout.CENTER);
		milestoneRow.add(milestonePatternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, milestoneRow);

		level99Row.add(level99CheckBox, BorderLayout.CENTER);
		level99Row.add(previewLevel99Button, BorderLayout.EAST);
		PanelUi.addVerticalComponent(settings, level99Row);
		return settings;
	}

	private void applyCustomPatternLibrary(CustomPatternLibrary updatedLibrary)
	{
		customPatterns = updatedLibrary;
		patternSelection = resolveAndPersist(
			patternSelection,
			HapticScapeConfig.PATTERN_PRESET_KEY
		);
		levelUpPatternSelection = resolveAndPersist(
			levelUpPatternSelection,
			HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY
		);
		milestonePatternSelection = resolveAndPersist(
			milestonePatternSelection,
			HapticScapeConfig.MILESTONE_PATTERN_PRESET_KEY
		);
		profilesPanel.applyCustomPatternLibrary(updatedLibrary);
		alertsPanel.applyCustomPatternLibrary(updatedLibrary);

		updatingPatternSelectors = true;
		try
		{
			PanelUi.setPatternChoices(patternComboBox, patternSelection, updatedLibrary);
			PanelUi.setPatternChoices(
				levelUpPatternComboBox,
				levelUpPatternSelection,
				updatedLibrary
			);
			PanelUi.setPatternChoices(
				milestonePatternComboBox,
				milestonePatternSelection,
				updatedLibrary
			);
		}
		finally
		{
			updatingPatternSelectors = false;
		}
	}

	private HapticPatternSelection resolveAndPersist(
		HapticPatternSelection current,
		String configKey)
	{
		HapticPatternSelection resolved = current.resolveAgainst(customPatterns);
		if (!resolved.equals(current))
		{
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				configKey,
				resolved.toConfigValue()
			);
		}
		return resolved;
	}

	private void refreshInheritedProfileIfReady()
	{
		if (profilesPanel != null)
		{
			profilesPanel.refreshInheritedProfile();
		}
	}

	public void close()
	{
		developerStatusTimer.stop();
		customPatternsPanel.close();
	}

	private void handleDeveloperUnlockClick(MouseEvent event)
	{
		if ((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0)
		{
			developerUnlockClickCount = 0;
			return;
		}

		long now = System.nanoTime();
		if (developerUnlockClickCount == 0
			|| now - developerUnlockStartedNanos > DEVELOPER_UNLOCK_WINDOW_NANOS)
		{
			developerUnlockStartedNanos = now;
			developerUnlockClickCount = 1;
		}
		else
		{
			developerUnlockClickCount++;
		}

		if (developerUnlockClickCount < DEVELOPER_UNLOCK_CLICKS)
		{
			return;
		}
		developerUnlockClickCount = 0;
		developerControlsUnlocked = !developerControlsUnlocked;
		previewLevel99Button.setVisible(developerControlsUnlocked);
		level99Row.revalidate();
		revalidate();
		repaint();
		statusLabel.setText(developerControlsUnlocked
			? "Developer controls unlocked"
			: "Developer controls locked");
		developerStatusTimer.restart();
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
		latestConnectionSnapshot = snapshot;
		if (!developerStatusTimer.isRunning())
		{
			statusLabel.setText(snapshot.getMessage());
		}

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
		profilesPanel.setConnected(connected);
		alertsPanel.setConnected(connected);
		customPatternsPanel.setConnected(connected);
		stopButton.setEnabled(connected);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
