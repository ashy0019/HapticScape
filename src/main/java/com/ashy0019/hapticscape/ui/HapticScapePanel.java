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
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.ConnectionState;
import com.ashy0019.hapticscape.device.DeviceInfo;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.MusicSyncSnapshot;
import com.ashy0019.hapticscape.rogue.KonamiCodeDetector;
import com.ashy0019.hapticscape.rogue.RogueFeedbackEvent;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import com.ashy0019.hapticscape.remote.SettingsLockListener;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.rogue.ui.RogueLauncherPanel;
import com.ashy0019.hapticscape.rogue.ui.RoguePanel;
import com.ashy0019.hapticscape.update.UpdateCheckService;
import com.ashy0019.hapticscape.update.UpdatePreferencesStore;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
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
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public final class HapticScapePanel extends PluginPanel
	implements RemoteSessionListener, SettingsLockListener
{
	private static final int DEVELOPER_UNLOCK_CLICKS = 9;
	private static final long DEVELOPER_UNLOCK_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(4);
	private static final String ROGUE_UNLOCKED_KEY = "rogueUnlocked";
	private static final String ROGUE_UNLOCK_STING_PLAYED_KEY = "rogueUnlockStingPlayed";
	private static final String NORMAL_CARD = "normal";
	private static final String REMOTE_CARD = "remote";
	private static final String ROGUE_CARD = "rogue";
	private static final String LOCAL_FEEDBACK_CARD = "localFeedback";
	private static final String REMOTE_FEEDBACK_CARD = "remoteFeedback";
	private static final int MINE_WORKSPACE_INDEX = 0;
	private static final int SUBJECT_WORKSPACE_INDEX = 1;
	private static final String SETTINGS_LOCKED_MESSAGE =
		"<html><b>Settings locked</b><br>Forge + Music<br>stay editable</html>";
	private static final String POST_SESSION_LOCK_MESSAGE =
		"<html><b>Post-session lock</b><br>armed after session</html>";

	private final HapticScapeConfig config;
	private final ConfigManager configManager;
	private final Consumer<RogueFeedbackEvent> rogueFeedbackAction;
	private final Runnable rogueUnlockSoundAction;
	private final KonamiCodeDetector konamiCodeDetector = new KonamiCodeDetector();
	private final KeyEventDispatcher rogueKeyDispatcher;
	private final JTabbedPane tabs;
	private final CardLayout contentLayout = new CardLayout();
	private final JPanel contentHost = new JPanel(contentLayout);
	private final CardLayout feedbackLayout = new CardLayout();
	private final JPanel feedbackHost = new JPanel(feedbackLayout);
	private final JTabbedPane controllerWorkspaceTabs = new JTabbedPane(JTabbedPane.TOP);
	private final JLabel statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);
	private final DefaultListModel<DeviceInfo> deviceModel = new DefaultListModel<>();
	private final JButton connectButton = new JButton("Connect");
	private final JButton disconnectButton = new JButton("Disconnect");
	private final JButton testButton = new JButton("Test pattern");
	private final JButton testLevelUpButton = new JButton("Test level-up");
	private final JButton previewLevel99Button = new JButton("Test 99");
	private final JButton resetRogueDiscoveryButton = new JButton("Reset Rogue");
	private final JButton clearSettingsLockButton = new JButton("Clear settings lock");
	private final JButton stopButton = new JButton("Stop now");
	private final JButton updatesButton = new JButton("Updates");
	private final JButton remoteButton = new JButton("Remote Control");
	private final JButton remoteBackButton = new JButton("Back to HapticScape");
	private final JPanel settingsLockBanner = new JPanel(new BorderLayout(6, 0));
	private final JLabel settingsLockLabel = new JLabel(SETTINGS_LOCKED_MESSAGE);
	private final JButton unlockSettingsButton = new JButton("Unlock");
	private final JPanel remoteBanner = new JPanel(new BorderLayout(6, 0));
	private final JLabel remoteBannerLabel = new JLabel();
	private final JButton remoteEmergencyButton = new JButton("Emergency Off");
	private final JButton remoteResumeButton = new JButton("Resume");
	private final JButton remoteEndButton = new JButton("End");
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
	private final JPanel developerControlsRow = new JPanel(new GridLayout(0, 1, 0, 4));
	private final JPanel settingsPanel;
	private final SkillsPanel skillsPanel;
	private final ProfilesPanel profilesPanel;
	private final AlertsPanel alertsPanel;
	private final CustomPatternsPanel customPatternsPanel;
	private final MusicPanel musicPanel;
	private final ClickerPanel clickerPanel;
	private final UpdatesPanel updatesPanel;
	private final RemoteSessionManager remoteSessionManager;
	private final SettingsLockService settingsLockService;
	private final RemoteControlPanel remoteControlPanel;
	private final RoguePanel roguePanel;
	private final RogueLauncherPanel rogueLauncher;
	private boolean rogueModeUnlocked;
	private boolean rogueViewActive;
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
	private boolean remoteReadOnly;
	private boolean displayingRemoteSettings;
	private boolean updatingDisplayedSettings;
	private boolean subjectWorkspaceSelected = true;
	private boolean controllerSubjectAvailable;
	private boolean updatingControllerWorkspaceTabs;

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
		RemoteSessionManager remoteSessionManager,
		SettingsLockService settingsLockService,
		Consumer<RogueFeedbackEvent> rogueFeedbackAction,
		Runnable rogueUnlockSoundAction,
		Runnable stopAction)
	{
		super();
		this.config = config;
		this.configManager = configManager;
		this.remoteSessionManager = remoteSessionManager;
		this.settingsLockService = settingsLockService;
		this.rogueFeedbackAction = rogueFeedbackAction;
		this.rogueUnlockSoundAction = rogueUnlockSoundAction;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
		updatesButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
		updatesButton.setToolTipText("Configure HapticScape client updates");
		remoteButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
		remoteButton.setToolTipText("Create or join an opt-in encrypted Remote Control session");
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
		resetRogueDiscoveryButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
		resetRogueDiscoveryButton.setToolTipText(
			"Forget Rogue discovery and the unlock sting so the Konami code can be tested again"
		);
		clearSettingsLockButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
		clearSettingsLockButton.setToolTipText(
			"Emergency recovery: remove the persistent feedback-settings lock"
		);
		developerControlsRow.setVisible(false);

		configureGlobalListeners();
		settingsPanel = createGlobalSettingsPanel();

		skillsPanel = new SkillsPanel(
			SkillSelection.fromConfigValue(config.disabledSkills()),
			SkillSelection.fromConfigValue(config.clickerDisabledSkills()),
			this::writeFeedbackSetting
		);
		profilesPanel = new ProfilesPanel(
			SkillFeedbackProfiles.fromConfigValue(config.skillFeedbackProfiles())
				.replaceMissingCustomPatterns(customPatterns),
			this::writeFeedbackSetting,
			this::getGlobalXpFeedbackSettings,
			() -> customPatterns,
			testSkillProfileAction
		);
		alertsPanel = new AlertsPanel(
			config,
			this::writeFeedbackSetting,
			() -> customPatterns,
			testGenericAlertAction,
			testSpecificAlertAction
		);
		customPatternsPanel = new CustomPatternsPanel(
			customPatterns,
			this::writeFeedbackSetting,
			patternForgePreviewAction,
			this::applyCustomPatternLibrary
		);
		musicPanel = new MusicPanel(
			config,
			this::writeFeedbackSetting,
			settings ->
			{
				if (!isSubjectWorkspaceActive())
				{
					musicSettingsAction.accept(settings);
				}
			}
		);
		clickerPanel = new ClickerPanel(
			config,
			this::writeFeedbackSetting,
			settings ->
			{
				if (!isSubjectWorkspaceActive())
				{
					clickerSettingsAction.accept(settings);
				}
			},
			testClickAction
		);
		updatesPanel = new UpdatesPanel(updatePreferencesStore, updateCheckService);
		remoteControlPanel = new RemoteControlPanel(config, configManager, remoteSessionManager);
		roguePanel = new RoguePanel(configManager, rogueFeedbackAction);
		rogueLauncher = new RogueLauncherPanel(this::toggleRogueView);

		tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
		tabs.putClientProperty(
			"FlatLaf.style",
			"tabInsets: 2,1,2,1; tabHeight: 26; tabAreaAlignment: center"
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

		settingsLockBanner.setBorder(BorderFactory.createTitledBorder("Settings access"));
		settingsLockBanner.add(settingsLockLabel, BorderLayout.CENTER);
		JPanel unlockButtonHost = new JPanel(new GridBagLayout());
		unlockButtonHost.setOpaque(false);
		unlockButtonHost.add(unlockSettingsButton);
		settingsLockBanner.add(unlockButtonHost, BorderLayout.EAST);
		settingsLockBanner.setVisible(false);
		PanelUi.addVerticalComponent(topPanel, settingsLockBanner);

		remoteBanner.setBorder(BorderFactory.createTitledBorder("Remote Control"));
		remoteBanner.add(remoteBannerLabel, BorderLayout.CENTER);
		JPanel remoteBannerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		remoteBannerButtons.add(remoteEmergencyButton);
		remoteBannerButtons.add(remoteResumeButton);
		remoteBannerButtons.add(remoteEndButton);
		remoteBanner.add(remoteBannerButtons, BorderLayout.SOUTH);
		remoteBanner.setVisible(false);
		PanelUi.addVerticalComponent(topPanel, remoteBanner);

		controllerWorkspaceTabs.putClientProperty(
			"FlatLaf.style",
			"tabHeight: 26; tabAreaAlignment: center"
		);
		controllerWorkspaceTabs.addTab("Mine", new JPanel());
		controllerWorkspaceTabs.addTab("Subject", new JPanel());
		controllerWorkspaceTabs.setSelectedIndex(SUBJECT_WORKSPACE_INDEX);
		controllerWorkspaceTabs.setPreferredSize(new Dimension(0, 31));
		controllerWorkspaceTabs.setMinimumSize(new Dimension(0, 31));
		controllerWorkspaceTabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
		controllerWorkspaceTabs.setVisible(false);
		controllerWorkspaceTabs.addChangeListener(event ->
		{
			if (!updatingControllerWorkspaceTabs)
			{
				selectControllerWorkspace(
					controllerWorkspaceTabs.getSelectedIndex() == SUBJECT_WORKSPACE_INDEX
				);
			}
		});
		PanelUi.addVerticalComponent(topPanel, controllerWorkspaceTabs);

		JPanel localFeedback = new JPanel();
		localFeedback.setLayout(new BoxLayout(localFeedback, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(localFeedback, settingsPanel);
		PanelUi.addVerticalComponent(localFeedback, tabs);

		JPanel remoteFeedback = new JPanel(new BorderLayout());
		remoteFeedback.setBorder(BorderFactory.createTitledBorder("Feedback settings"));
		JLabel remoteFeedbackLabel = new JLabel(
			"<html><center>Settings are controlled by the paired controller.<br>"
				+ "Intiface connection and Emergency Off remain local.</center></html>",
			SwingConstants.CENTER
		);
		remoteFeedback.add(remoteFeedbackLabel, BorderLayout.CENTER);
		feedbackHost.add(localFeedback, LOCAL_FEEDBACK_CARD);
		feedbackHost.add(remoteFeedback, REMOTE_FEEDBACK_CARD);
		PanelUi.addVerticalComponent(topPanel, feedbackHost);

		JList<DeviceInfo> deviceList = new JList<>(deviceModel);
		JScrollPane scrollPane = new JScrollPane(deviceList);
		scrollPane.setPreferredSize(new Dimension(0, 140));
		scrollPane.setBorder(BorderFactory.createTitledBorder("Devices"));

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
		resetRogueDiscoveryButton.addActionListener(event -> resetRogueDiscovery());
		clearSettingsLockButton.addActionListener(event -> clearSettingsLockFromDeveloperMode());
		unlockSettingsButton.addActionListener(event -> unlockSettings());
		stopButton.addActionListener(event ->
		{
			if (remoteSessionManager.getSnapshot().isParticipantControlled())
			{
				remoteSessionManager.emergencyPause();
				return;
			}
			musicPanel.disableMusicSync();
			stopAction.run();
		});
		remoteButton.addActionListener(event -> showRemoteView());
		remoteBackButton.addActionListener(event -> showNormalView());
		remoteEmergencyButton.addActionListener(event -> remoteSessionManager.emergencyPause());
		remoteResumeButton.addActionListener(event -> remoteSessionManager.resumeParticipant());
		remoteEndButton.addActionListener(event -> remoteSessionManager.endSession());

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
		PanelUi.addVerticalComponent(buttons, remoteButton);

		JPanel normalView = new JPanel(new BorderLayout(0, 8));
		normalView.add(topPanel, BorderLayout.NORTH);
		normalView.add(scrollPane, BorderLayout.CENTER);
		normalView.add(buttons, BorderLayout.SOUTH);

		JPanel remoteView = new JPanel(new BorderLayout(0, 6));
		remoteBackButton.setToolTipText("Return to the main HapticScape controls");
		remoteView.add(remoteBackButton, BorderLayout.NORTH);
		JScrollPane remoteScrollPane = new JScrollPane(
			remoteControlPanel,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
		);
		remoteScrollPane.setBorder(BorderFactory.createEmptyBorder());
		remoteScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		remoteView.add(remoteScrollPane, BorderLayout.CENTER);

		contentHost.add(normalView, NORMAL_CARD);
		contentHost.add(remoteView, REMOTE_CARD);
		contentHost.add(roguePanel, ROGUE_CARD);
		add(rogueLauncher, BorderLayout.NORTH);
		add(contentHost, BorderLayout.CENTER);
		contentLayout.show(contentHost, NORMAL_CARD);

		if (Boolean.parseBoolean(configManager.getConfiguration(HapticScapeConfig.GROUP, ROGUE_UNLOCKED_KEY)))
		{
			ensureRogueAccess(false, false);
		}

		remoteSessionManager.addListener(this);
		settingsLockService.addListener(this);
		applyRemoteSessionState(remoteSessionManager.getSnapshot());
		applyState(ConnectionSnapshot.disconnected());
		rogueKeyDispatcher = this::handleRogueKeyEvent;
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.addKeyEventDispatcher(rogueKeyDispatcher);
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
		return isHapticSkillEnabled(skill);
	}

	public boolean isHapticSkillEnabled(Skill skill)
	{
		return skillsPanel.isHapticSkillEnabled(skill);
	}

	public boolean isClickSkillEnabled(Skill skill)
	{
		return skillsPanel.isClickSkillEnabled(skill);
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

	public boolean isGenericNotificationClickEnabled()
	{
		return alertsPanel.isGenericClickEnabled();
	}

	public boolean isAlertClickEnabled(AlertCategory category)
	{
		return alertsPanel.isClickEnabled(category);
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

	public ClickerXpSettings getClickerXpSettings()
	{
		return clickerPanel.getXpSettings();
	}


	public ClickerPhraseRules getClickerPhraseRules()
	{
		return clickerPanel.getPhraseRules();
	}

	public void updateMusicSync(MusicSyncSnapshot snapshot)
	{
		musicPanel.updateSnapshot(snapshot);
	}

	private void configureGlobalListeners()
	{
		intensitySlider.addChangeListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			intensityPercent = intensitySlider.getValue();
			intensityValueLabel.setText(intensityPercent + "%");
			refreshInheritedProfileIfReady();
			if (!intensitySlider.getValueIsAdjusting())
			{
				writeFeedbackSetting(
					HapticScapeConfig.INTENSITY_PERCENT_KEY,
					intensityPercent
				);
			}
		});
		minimumXpSpinner.addChangeListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			minimumXpGain = ((Number) minimumXpSpinner.getValue()).intValue();
			writeFeedbackSetting(
				HapticScapeConfig.MINIMUM_XP_GAIN_KEY,
				minimumXpGain
			);
			refreshInheritedProfileIfReady();
		});
		durationSpinner.addChangeListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			durationMillis = ((Number) durationSpinner.getValue()).intValue();
			writeFeedbackSetting(
				HapticScapeConfig.PULSE_DURATION_MILLIS_KEY,
				durationMillis
			);
			refreshInheritedProfileIfReady();
		});
		patternComboBox.addActionListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			if (updatingPatternSelectors)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) patternComboBox.getSelectedItem();
			if (selected != null)
			{
				patternSelection = selected;
				writeFeedbackSetting(
					HapticScapeConfig.PATTERN_PRESET_KEY,
					selected.toConfigValue()
				);
				refreshInheritedProfileIfReady();
			}
		});
		levelUpPatternComboBox.addActionListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			if (!updatingPatternSelectors)
			{
				HapticPatternSelection selected =
					(HapticPatternSelection) levelUpPatternComboBox.getSelectedItem();
				if (selected != null)
				{
					levelUpPatternSelection = selected;
					writeFeedbackSetting(
						HapticScapeConfig.LEVEL_UP_PATTERN_PRESET_KEY,
						selected.toConfigValue()
					);
				}
			}
		});
		milestonePatternComboBox.addActionListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			if (!updatingPatternSelectors)
			{
				HapticPatternSelection selected =
					(HapticPatternSelection) milestonePatternComboBox.getSelectedItem();
				if (selected != null)
				{
					milestonePatternSelection = selected;
					writeFeedbackSetting(
						HapticScapeConfig.MILESTONE_PATTERN_PRESET_KEY,
						selected.toConfigValue()
					);
				}
			}
		});
		levelUpCheckBox.addActionListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			levelUpEnabled = levelUpCheckBox.isSelected();
			writeFeedbackSetting(
				HapticScapeConfig.LEVEL_UP_FEEDBACK_ENABLED_KEY,
				levelUpEnabled
			);
		});
		milestoneCheckBox.addActionListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			milestoneEnabled = milestoneCheckBox.isSelected();
			writeFeedbackSetting(
				HapticScapeConfig.MILESTONE_FEEDBACK_ENABLED_KEY,
				milestoneEnabled
			);
		});
		level99CheckBox.addActionListener(event ->
		{
			if (updatingDisplayedSettings || remoteReadOnly)
			{
				return;
			}
			level99Enabled = level99CheckBox.isSelected();
			writeFeedbackSetting(
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

	private void writeFeedbackSetting(String key, Object value)
	{
		RemoteSessionSnapshot current = remoteSessionManager.getSnapshot();
		if (isSubjectWorkspaceActive(current))
		{
			remoteSessionManager.updateControllerSetting(key, value);
			return;
		}
		if (current.getRole() == RemoteRole.PARTICIPANT
			&& current.getState() != RemoteSessionState.LOCAL)
		{
			return;
		}
		if (!settingsLockService.canEditLocally(key))
		{
			return;
		}
		configManager.setConfiguration(HapticScapeConfig.GROUP, key, value);
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
		PanelUi.addVerticalComponent(settings, level99Row);

		developerControlsRow.add(previewLevel99Button);
		developerControlsRow.add(resetRogueDiscoveryButton);
		developerControlsRow.add(clearSettingsLockButton);
		PanelUi.addVerticalComponent(settings, developerControlsRow);
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
			writeFeedbackSetting(
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
		remoteSessionManager.removeListener(this);
		settingsLockService.removeListener(this);
		remoteControlPanel.close();
		developerStatusTimer.stop();
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.removeKeyEventDispatcher(rogueKeyDispatcher);
		roguePanel.close();
		rogueLauncher.close();
		customPatternsPanel.close();
	}

	private boolean handleRogueKeyEvent(KeyEvent event)
	{
		if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed())
		{
			return false;
		}

		Window panelWindow = SwingUtilities.getWindowAncestor(this);
		if (panelWindow == null
			|| KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != panelWindow)
		{
			return false;
		}

		if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent)
		{
			konamiCodeDetector.reset();
			return false;
		}

		if (konamiCodeDetector.acceptKeyCode(event.getKeyCode()))
		{
			SwingUtilities.invokeLater(() -> unlockRogueMode(true));
		}
		return false;
	}

	private void unlockRogueMode(boolean celebrate)
	{
		boolean firstUnlock = !rogueModeUnlocked;
		boolean unlockStingPlayed = Boolean.parseBoolean(configManager.getConfiguration(
			HapticScapeConfig.GROUP,
			ROGUE_UNLOCK_STING_PLAYED_KEY
		));
		if (!unlockStingPlayed && rogueUnlockSoundAction != null)
		{
			rogueUnlockSoundAction.run();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				ROGUE_UNLOCK_STING_PLAYED_KEY,
				true
			);
		}
		ensureRogueAccess(true, firstUnlock);
		configManager.setConfiguration(HapticScapeConfig.GROUP, ROGUE_UNLOCKED_KEY, true);
		rogueLauncher.celebrate();
		roguePanel.reveal();
		if (celebrate && rogueFeedbackAction != null)
		{
			rogueFeedbackAction.accept(RogueFeedbackEvent.UNLOCK);
		}
		statusLabel.setText(firstUnlock ? "Rogue Mode unlocked" : "Rogue Mode summoned");
		developerStatusTimer.restart();
	}

	private void ensureRogueAccess(boolean showRogue, boolean animateEmergence)
	{
		if (!rogueModeUnlocked)
		{
			rogueModeUnlocked = true;
			rogueLauncher.showUnlocked(animateEmergence);
		}
		else if (!rogueLauncher.isVisible())
		{
			rogueLauncher.showUnlocked(false);
		}

		if (showRogue)
		{
			showRogueView();
		}
	}

	private void toggleRogueView()
	{
		if (!rogueModeUnlocked)
		{
			return;
		}
		if (rogueViewActive)
		{
			showNormalView();
		}
		else
		{
			showRogueView();
		}
	}

	private void showRogueView()
	{
		rogueViewActive = true;
		contentLayout.show(contentHost, ROGUE_CARD);
		rogueLauncher.setActive(true);
		contentHost.revalidate();
		contentHost.repaint();
	}

	private void showNormalView()
	{
		rogueViewActive = false;
		contentLayout.show(contentHost, NORMAL_CARD);
		rogueLauncher.setActive(false);
		contentHost.revalidate();
		contentHost.repaint();
	}

	private void showRemoteView()
	{
		rogueViewActive = false;
		contentLayout.show(contentHost, REMOTE_CARD);
		rogueLauncher.setActive(false);
		contentHost.revalidate();
		contentHost.repaint();
	}

	private void resetRogueDiscovery()
	{
		showNormalView();
		rogueModeUnlocked = false;
		konamiCodeDetector.reset();
		rogueLauncher.resetLocked();
		configManager.setConfiguration(HapticScapeConfig.GROUP, ROGUE_UNLOCKED_KEY, false);
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			ROGUE_UNLOCK_STING_PLAYED_KEY,
			false
		);
		statusLabel.setText("Rogue discovery reset - enter the Konami code again");
		developerStatusTimer.restart();
	}

	private void unlockSettings()
	{
		JPasswordField passwordField = new JPasswordField(18);
		int choice = JOptionPane.showConfirmDialog(
			this,
			passwordField,
			"Enter settings unlock key",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		char[] password = passwordField.getPassword();
		try
		{
			if (!settingsLockService.unlock(password))
			{
				JOptionPane.showMessageDialog(
					this,
					"That key did not unlock these settings.",
					"Settings remain locked",
					JOptionPane.ERROR_MESSAGE
				);
			}
		}
		catch (RuntimeException e)
		{
			JOptionPane.showMessageDialog(
				this,
				e.getMessage(),
				"Unable to unlock settings",
				JOptionPane.ERROR_MESSAGE
			);
		}
		finally
		{
			Arrays.fill(password, '\0');
			passwordField.setText("");
		}
	}

	private void clearSettingsLockFromDeveloperMode()
	{
		if (!settingsLockService.isLocked())
		{
			return;
		}
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Clear the persistent settings lock? The current feedback settings "
				+ "will not be changed.",
			"Emergency settings-lock recovery",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (choice == JOptionPane.YES_OPTION)
		{
			settingsLockService.clearAllLocks();
			statusLabel.setText("Settings lock cleared through developer recovery");
			developerStatusTimer.restart();
		}
	}

	private void refreshDeveloperControlsLayout()
	{
		developerControlsRow.revalidate();
		java.awt.Container parent = developerControlsRow.getParent();
		if (parent instanceof JPanel)
		{
			JPanel settingsPanel = (JPanel) parent;
			Dimension preferredSize = settingsPanel.getPreferredSize();
			settingsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
			settingsPanel.revalidate();
		}
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
		developerControlsRow.setVisible(developerControlsUnlocked);
		refreshDeveloperControlsLayout();
		revalidate();
		repaint();
		statusLabel.setText(developerControlsUnlocked
			? "Developer controls unlocked"
			: "Developer controls locked");
		developerStatusTimer.restart();
	}

	@Override
	public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> applyRemoteSessionState(snapshot));
			return;
		}
		applyRemoteSessionState(snapshot);
	}

	@Override
	public void onSettingsLockChanged(boolean locked)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> applySettingsLockState(locked));
			return;
		}
		applySettingsLockState(locked);
	}

	@Override
	public void onRemoteSettingsChanged(RemoteSettingsSnapshot settings)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> applyRemoteSettingsIfActive(settings));
			return;
		}
		applyRemoteSettingsIfActive(settings);
	}

	private void applyRemoteSettingsIfActive(RemoteSettingsSnapshot settings)
	{
		// Re-check on the EDT so a queued remote update cannot repaint stale remote
		// values after the participant has already ended the session.
		RemoteSessionSnapshot current = remoteSessionManager.getSnapshot();
		if (current.isParticipantControlled() || isSubjectWorkspaceActive(current))
		{
			displayingRemoteSettings = true;
			applyDisplayedSettings(settings);
		}
	}

	private void applyDisplayedSettings(RemoteSettingsSnapshot settings)
	{
		CustomPatternLibrary displayedPatterns = settings.getCustomPatterns();
		XpFeedbackSettings global = settings.getGlobalXpFeedbackSettings();

		updatingDisplayedSettings = true;
		try
		{
			customPatterns = displayedPatterns;
			minimumXpGain = global.getMinimumXpGain();
			intensityPercent = global.getIntensityPercent();
			durationMillis = global.getDurationMillis();
			patternSelection = global.getPatternSelection();
			levelUpEnabled = settings.isLevelUpFeedbackEnabled();
			milestoneEnabled = settings.isMilestoneFeedbackEnabled();
			level99Enabled = settings.isLevel99CelebrationEnabled();
			levelUpPatternSelection = settings.getLevelUpPatternPreset();
			milestonePatternSelection = settings.getMilestonePatternPreset();

			minimumXpSpinner.setValue(minimumXpGain);
			intensitySlider.setValue(intensityPercent);
			intensityValueLabel.setText(intensityPercent + "%");
			durationSpinner.setValue(durationMillis);
			levelUpCheckBox.setSelected(levelUpEnabled);
			milestoneCheckBox.setSelected(milestoneEnabled);
			level99CheckBox.setSelected(level99Enabled);

			updatingPatternSelectors = true;
			try
			{
				PanelUi.setPatternChoices(patternComboBox, patternSelection, displayedPatterns);
				PanelUi.setPatternChoices(
					levelUpPatternComboBox,
					levelUpPatternSelection,
					displayedPatterns
				);
				PanelUi.setPatternChoices(
					milestonePatternComboBox,
					milestonePatternSelection,
					displayedPatterns
				);
			}
			finally
			{
				updatingPatternSelectors = false;
			}

			skillsPanel.applyDisplayedSelections(
				settings.getHapticSkillSelection(),
				settings.getClickSkillSelection()
			);
			profilesPanel.applyDisplayedSettings(
				settings.getSkillFeedbackProfiles(),
				displayedPatterns
			);
			alertsPanel.applyDisplayedSettings(
				settings.getNotificationFeedbackSettings(),
				settings.isGenericNotificationClickEnabled(),
				settings.getAlertProfiles(),
				settings.getAlertTriggerSettings(),
				settings.getClickerAlertSettings(),
				displayedPatterns
			);
			customPatternsPanel.applyDisplayedLibrary(displayedPatterns);
			musicPanel.applyDisplayedSettings(settings.getMusicSyncSettings());
			clickerPanel.applyDisplayedSettings(
				settings.getClickerSettings(),
				settings.getClickerXpSettings(),
				settings.getClickerPhraseRules()
			);
		}
		finally
		{
			updatingDisplayedSettings = false;
		}
	}

	private void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		boolean editable = !remoteReadOnly;
		minimumXpSpinner.setEnabled(editable);
		intensitySlider.setEnabled(editable);
		intensityValueLabel.setEnabled(editable);
		durationSpinner.setEnabled(editable);
		patternComboBox.setEnabled(editable);
		levelUpCheckBox.setEnabled(editable);
		levelUpPatternComboBox.setEnabled(editable);
		milestoneCheckBox.setEnabled(editable);
		milestonePatternComboBox.setEnabled(editable);
		level99CheckBox.setEnabled(editable);
		skillsPanel.setRemoteReadOnly(remoteReadOnly);
		profilesPanel.setRemoteReadOnly(remoteReadOnly);
		alertsPanel.setRemoteReadOnly(remoteReadOnly);
		customPatternsPanel.setRemoteReadOnly(remoteReadOnly);
		musicPanel.setRemoteReadOnly(remoteReadOnly);
		clickerPanel.setRemoteReadOnly(remoteReadOnly);
		// Tabs, navigation selectors, Casino/Rogue, Updates, Intiface connection,
		// Remote Control controls, and Emergency Off intentionally remain usable.
	}

	private void setLocalSettingsLocked(boolean locked)
	{
		setRemoteReadOnly(locked);
		if (locked)
		{
			// These remain participant-owned after a session. In particular, music
			// sync can always be disabled or retuned locally.
			customPatternsPanel.setRemoteReadOnly(false);
			musicPanel.setRemoteReadOnly(false);
		}
	}

	private boolean isSubjectWorkspaceActive()
	{
		return isSubjectWorkspaceActive(remoteSessionManager.getSnapshot());
	}

	private boolean isSubjectWorkspaceActive(RemoteSessionSnapshot snapshot)
	{
		return subjectWorkspaceSelected
			&& isControllerSubjectAvailable(snapshot);
	}

	private boolean isControllerSubjectAvailable(RemoteSessionSnapshot snapshot)
	{
		return snapshot.getRole() == RemoteRole.CONTROLLER
			&& (snapshot.getState() == RemoteSessionState.ACTIVE
				|| snapshot.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED)
			&& remoteSessionManager.getControllerSettingsSnapshot() != null;
	}

	private void selectControllerWorkspace(boolean subject)
	{
		RemoteSessionSnapshot snapshot = remoteSessionManager.getSnapshot();
		if (snapshot.getRole() != RemoteRole.CONTROLLER
			|| snapshot.getState() == RemoteSessionState.LOCAL
			|| snapshot.getState() == RemoteSessionState.DISCONNECTED)
		{
			return;
		}
		if (subject && !isControllerSubjectAvailable(snapshot))
		{
			setControllerWorkspaceTab(false);
			return;
		}

		subjectWorkspaceSelected = subject;
		if (subject)
		{
			RemoteSettingsSnapshot subjectSettings =
				remoteSessionManager.getControllerSettingsSnapshot();
			if (subjectSettings != null)
			{
				displayingRemoteSettings = true;
				applyDisplayedSettings(subjectSettings);
			}
		}
		else
		{
			applyDisplayedSettings(RemoteSettingsSnapshot.capture(config));
			displayingRemoteSettings = false;
		}
		applyRemoteSessionState(snapshot);
	}

	private void setControllerWorkspaceTab(boolean subject)
	{
		updatingControllerWorkspaceTabs = true;
		try
		{
			controllerWorkspaceTabs.setSelectedIndex(
				subject ? SUBJECT_WORKSPACE_INDEX : MINE_WORKSPACE_INDEX
			);
		}
		finally
		{
			updatingControllerWorkspaceTabs = false;
		}
	}

	private void applyRemoteSessionState(RemoteSessionSnapshot snapshot)
	{
		boolean participantControlled = snapshot.isParticipantControlled();
		boolean controllerSession = snapshot.getRole() == RemoteRole.CONTROLLER
			&& snapshot.getState() != RemoteSessionState.LOCAL;
		boolean controllerEditable = controllerSession
			&& (snapshot.getState() == RemoteSessionState.ACTIVE
				|| snapshot.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED);
		boolean workspaceVisible = controllerSession
			&& snapshot.getState() != RemoteSessionState.DISCONNECTED;
		boolean subjectAvailable = isControllerSubjectAvailable(snapshot);
		stopButton.setText(participantControlled ? "Emergency Off" : "Stop now");
		boolean workspaceWasVisible = controllerWorkspaceTabs.isVisible();
		boolean subjectBecameAvailable = subjectAvailable && !controllerSubjectAvailable;
		controllerSubjectAvailable = subjectAvailable;
		controllerWorkspaceTabs.setVisible(workspaceVisible);
		controllerWorkspaceTabs.setEnabledAt(MINE_WORKSPACE_INDEX, true);
		controllerWorkspaceTabs.setEnabledAt(SUBJECT_WORKSPACE_INDEX, subjectAvailable);
		if (workspaceVisible && subjectAvailable
			&& (!workspaceWasVisible || subjectBecameAvailable))
		{
			subjectWorkspaceSelected = true;
			setControllerWorkspaceTab(true);
			RemoteSettingsSnapshot subjectSettings =
				remoteSessionManager.getControllerSettingsSnapshot();
			if (subjectSettings != null)
			{
				displayingRemoteSettings = true;
				applyDisplayedSettings(subjectSettings);
			}
		}
		else if (workspaceVisible && !subjectAvailable
			&& (!workspaceWasVisible || subjectWorkspaceSelected))
		{
			subjectWorkspaceSelected = false;
			setControllerWorkspaceTab(false);
			if (displayingRemoteSettings)
			{
				applyDisplayedSettings(RemoteSettingsSnapshot.capture(config));
			}
			displayingRemoteSettings = false;
		}
		else if (!workspaceVisible)
		{
			controllerSubjectAvailable = false;
		}

		// Keep the normal HapticScape UI visible during Remote Control. The
		// participant can navigate it and watch remote values change, but cannot
		// mutate remotely authoritative feedback settings.
		feedbackLayout.show(feedbackHost, LOCAL_FEEDBACK_CARD);
		if (participantControlled)
		{
			setRemoteReadOnly(true);
		}
		else if (controllerEditable)
		{
			if (subjectWorkspaceSelected)
			{
				setRemoteReadOnly(false);
			}
			else
			{
				setLocalSettingsLocked(settingsLockService.isLocked());
			}
		}
		else
		{
			if (displayingRemoteSettings)
			{
				applyDisplayedSettings(RemoteSettingsSnapshot.capture(config));
			}
			displayingRemoteSettings = false;
			setLocalSettingsLocked(settingsLockService.isLocked());
			if (!controllerSession)
			{
				subjectWorkspaceSelected = true;
				setControllerWorkspaceTab(true);
			}
		}

		// Connection management stays local even during Remote Control. Manual
		// preview buttons are disabled so they cannot bypass the remote policy.
		boolean connected = latestConnectionSnapshot.getState() == ConnectionState.CONNECTED;
		boolean previewBlocked = participantControlled || isSubjectWorkspaceActive(snapshot);
		profilesPanel.setPreviewAllowed(!previewBlocked);
		alertsPanel.setPreviewAllowed(!previewBlocked);
		customPatternsPanel.setPreviewAllowed(!previewBlocked);
		clickerPanel.setPreviewAllowed(!previewBlocked);
		boolean emergencyPaused = snapshot.getState() == RemoteSessionState.EMERGENCY_PAUSED;
		stopButton.setEnabled(participantControlled ? !emergencyPaused : connected);
		testButton.setEnabled(!previewBlocked && connected);
		testLevelUpButton.setEnabled(!previewBlocked && connected);
		previewLevel99Button.setEnabled(
			!previewBlocked && developerControlsUnlocked && connected
		);

		boolean showBanner = snapshot.getState() != RemoteSessionState.LOCAL;
		remoteBanner.setVisible(showBanner);
		remoteBannerLabel.setText("<html>" + snapshot.getMessage()
			+ (participantControlled
				? "<br><small>Changes are saved locally and remain after the session.</small>"
				: "")
			+ "</html>");
		boolean participant = snapshot.getRole() == RemoteRole.PARTICIPANT && showBanner;
		remoteEmergencyButton.setEnabled(participant && !emergencyPaused);
		remoteResumeButton.setEnabled(participant && emergencyPaused);
		remoteEmergencyButton.setVisible(participant && !emergencyPaused);
		remoteResumeButton.setVisible(participant && emergencyPaused);
		remoteEndButton.setEnabled(showBanner);
		applySettingsLockState(settingsLockService.isLocked());
		revalidate();
		repaint();
	}

	private void applySettingsLockState(boolean locked)
	{
		RemoteSessionSnapshot remote = remoteSessionManager.getSnapshot();
		boolean local = remote.getState() == RemoteSessionState.LOCAL;
		boolean participantSession = remote.getRole() == RemoteRole.PARTICIPANT && !local;
		boolean controllerMine = remote.getRole() == RemoteRole.CONTROLLER
			&& remote.getState() != RemoteSessionState.LOCAL
			&& !isSubjectWorkspaceActive(remote);
		boolean localWorkspace = local || controllerMine;

		settingsLockLabel.setText(participantSession
			? POST_SESSION_LOCK_MESSAGE
			: SETTINGS_LOCKED_MESSAGE);
		settingsLockBanner.setVisible(locked && (localWorkspace || participantSession));
		unlockSettingsButton.setVisible(locked && localWorkspace);
		unlockSettingsButton.setEnabled(locked && localWorkspace);
		clearSettingsLockButton.setEnabled(locked);
		if (localWorkspace)
		{
			setLocalSettingsLocked(locked);
		}
		revalidate();
		repaint();
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
		applyRemoteSessionState(remoteSessionManager.getSnapshot());
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
