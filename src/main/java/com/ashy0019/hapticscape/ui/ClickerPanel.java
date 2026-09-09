package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

final class ClickerPanel extends JPanel
{
	private static final int WIDE_BREAKPOINT = 1250;
	private static final int MEDIUM_BREAKPOINT = 760;

	private final SettingsChangeSink settingsSink;
	private final Consumer<ClickerSettings> settingsListener;
	private final JPanel layoutPanel = new JPanel(new GridBagLayout());
	private final JCheckBox enabledCheckBox = new JCheckBox("Enable clicker");
	private final JSlider volumeSlider = new JSlider(
		ClickerSettings.MINIMUM_VOLUME_PERCENT,
		ClickerSettings.MAXIMUM_VOLUME_PERCENT
	);
	private final JLabel volumeValue = new JLabel();
	private final JSpinner minimumXpSpinner = new JSpinner(new SpinnerNumberModel(
		ClickerXpSettings.MINIMUM_XP_GAIN,
		ClickerXpSettings.MINIMUM_XP_GAIN,
		ClickerXpSettings.MAXIMUM_XP_GAIN,
		1
	));
	private final JCheckBox levelUpCheckBox = new JCheckBox("Always click level-ups");
	private final JCheckBox milestoneCheckBox = new JCheckBox("Always click milestones");
	private final JCheckBox level99CheckBox = new JCheckBox("Always click level 99");
	private final JButton testButton = new JButton("Test click");
	private volatile ClickerSettings settings;
	private volatile ClickerXpSettings xpSettings;
	private final ClickerPhraseRulesPanel phraseRulesPanel;
	private final LockableCheckBoxBinding enabledLockBinding;
	private final LockableCheckBoxBinding levelUpLockBinding;
	private final LockableCheckBoxBinding milestoneLockBinding;
	private final LockableCheckBoxBinding level99LockBinding;
	private final LockableSectionHeader clickSettingsBlockHeader;
	private boolean updating;
	private boolean remoteReadOnly;
	private boolean previewAllowed = true;
	private int layoutMode = -1;

	ClickerPanel(
		HapticScapeSettingsSource config,
		SettingsChangeSink settingsSink,
		Consumer<ClickerSettings> settingsListener,
		Runnable testAction,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.settingsSink = settingsSink;
		this.settingsListener = settingsListener;
		phraseRulesPanel = new ClickerPhraseRulesPanel(
			config,
			settingsSink,
			sessionManager,
			lockService,
			lockDraft,
			editingRemoteSubject,
			lockSelectionEnabled
		);
		setName("clicksWorkspace");
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		settings = new ClickerSettings(
			config.clickerEnabled(),
			config.clickerVolumePercent()
		);
		xpSettings = new ClickerXpSettings(
			config.clickerMinimumXpGain(),
			config.clickerLevelUpEnabled(),
			config.clickerMilestoneEnabled(),
			config.clickerLevel99Enabled()
		);
		enabledCheckBox.setSelected(settings.isEnabled());
		volumeSlider.setValue(clamp(
			settings.getVolumePercent(),
			ClickerSettings.MINIMUM_VOLUME_PERCENT,
			ClickerSettings.MAXIMUM_VOLUME_PERCENT
		));
		minimumXpSpinner.setValue(clamp(
			xpSettings.getMinimumXpGain(),
			ClickerXpSettings.MINIMUM_XP_GAIN,
			ClickerXpSettings.MAXIMUM_XP_GAIN
		));
		PanelUi.setFixedWidth(minimumXpSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		levelUpCheckBox.setSelected(xpSettings.isLevelUpEnabled());
		milestoneCheckBox.setSelected(xpSettings.isMilestoneEnabled());
		level99CheckBox.setSelected(xpSettings.isLevel99Enabled());
		levelUpCheckBox.setToolTipText("Click even when a level-up XP gain is below the threshold");
		milestoneCheckBox.setToolTipText("Give decade milestones priority over ordinary level-ups");
		level99CheckBox.setToolTipText("Click once when a skill reaches level 99");
		enabledLockBinding = binding(
			enabledCheckBox,
			SettingsLockCatalog.CLICKER_ENABLED,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> settings.isEnabled()
		);
		levelUpLockBinding = binding(
			levelUpCheckBox,
			SettingsLockCatalog.CLICKER_LEVEL_UP,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> xpSettings.isLevelUpEnabled()
		);
		milestoneLockBinding = binding(
			milestoneCheckBox,
			SettingsLockCatalog.CLICKER_MILESTONE,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> xpSettings.isMilestoneEnabled()
		);
		level99LockBinding = binding(
			level99CheckBox,
			SettingsLockCatalog.CLICKER_LEVEL_99,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> xpSettings.isLevel99Enabled()
		);
		clickSettingsBlockHeader = new LockableSectionHeader(
			"",
			() -> SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled
		);

		JPanel clickOutputPanel = new JPanel();
		clickOutputPanel.setLayout(new BoxLayout(clickOutputPanel, BoxLayout.Y_AXIS));
		clickOutputPanel.setBorder(PanelUi.createSectionBorder("Click output"));
		PanelUi.addPreferredHeightComponent(clickOutputPanel, clickSettingsBlockHeader);
		PanelUi.addPreferredHeightComponent(clickOutputPanel, enabledCheckBox);
		PanelUi.addPreferredHeightComponent(clickOutputPanel, row("Volume", volumeValue));
		PanelUi.addPreferredHeightComponent(clickOutputPanel, volumeSlider);
		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testButton, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(clickOutputPanel, testRow);
		JLabel description = new JLabel("Independent of Intiface and haptic devices.");
		description.setToolTipText("Click playback is ordinary local audio");
		PanelUi.addPreferredHeightComponent(clickOutputPanel, description);

		JPanel xpSettingsPanel = new JPanel();
		xpSettingsPanel.setLayout(new BoxLayout(xpSettingsPanel, BoxLayout.Y_AXIS));
		xpSettingsPanel.setBorder(PanelUi.createSectionBorder("XP clicks"));
		JLabel skillHint = new JLabel("Choose skills in XP + Skills → Clicks.");
		skillHint.setToolTipText("Each skill has separate Haptics and Clicks switches");
		PanelUi.addPreferredHeightComponent(xpSettingsPanel, skillHint);
		PanelUi.addPreferredHeightComponent(
			xpSettingsPanel,
			row("Minimum XP gain", minimumXpSpinner)
		);
		PanelUi.addPreferredHeightComponent(xpSettingsPanel, levelUpCheckBox);
		PanelUi.addPreferredHeightComponent(xpSettingsPanel, milestoneCheckBox);
		PanelUi.addPreferredHeightComponent(xpSettingsPanel, level99CheckBox);

		JPanel outputHost = host(clickOutputPanel, 300);
		JPanel xpHost = host(xpSettingsPanel, 330);
		JPanel phrasesHost = host(phraseRulesPanel, 680);
		add(layoutPanel, BorderLayout.NORTH);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflow(outputHost, xpHost, phrasesHost);
			}
		});
		reflow(outputHost, xpHost, phrasesHost);

		refreshLabel();
		refreshEnabledState();
		configureListeners(testAction);
	}

	void applyDisplayedSettings(
		ClickerSettings displayedSettings,
		ClickerXpSettings displayedXpSettings,
		ClickerPhraseRules displayedPhraseRules)
	{
		settings = displayedSettings;
		xpSettings = displayedXpSettings;
		updating = true;
		try
		{
			enabledCheckBox.setSelected(displayedSettings.isEnabled());
			volumeSlider.setValue(clamp(
				displayedSettings.getVolumePercent(),
				ClickerSettings.MINIMUM_VOLUME_PERCENT,
				ClickerSettings.MAXIMUM_VOLUME_PERCENT
			));
			minimumXpSpinner.setValue(clamp(
				displayedXpSettings.getMinimumXpGain(),
				ClickerXpSettings.MINIMUM_XP_GAIN,
				ClickerXpSettings.MAXIMUM_XP_GAIN
			));
			levelUpCheckBox.setSelected(displayedXpSettings.isLevelUpEnabled());
			milestoneCheckBox.setSelected(displayedXpSettings.isMilestoneEnabled());
			level99CheckBox.setSelected(displayedXpSettings.isLevel99Enabled());
			phraseRulesPanel.applyDisplayedRules(displayedPhraseRules);
			refreshLabel();
		}
		finally
		{
			updating = false;
		}
		refreshEnabledState();
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		phraseRulesPanel.setRemoteReadOnly(remoteReadOnly);
		refreshEnabledState();
	}

	void setPreviewAllowed(boolean previewAllowed)
	{
		this.previewAllowed = previewAllowed;
		refreshEnabledState();
	}

	ClickerSettings getSettings()
	{
		return settings;
	}

	ClickerXpSettings getXpSettings()
	{
		return xpSettings;
	}


	ClickerPhraseRules getPhraseRules()
	{
		return phraseRulesPanel.getRules();
	}

	private void configureListeners(Runnable testAction)
	{
		enabledCheckBox.addActionListener(event ->
		{
			if (updating || enabledLockBinding.handleAction(event))
			{
				return;
			}
			if (isClickSettingsReadOnly() || enabledLockBinding.isEditLocked())
			{
				return;
			}
			persist(
				SettingsLockCatalog.CLICKER_ENABLED,
				HapticScapeSettingKeys.CLICKER_ENABLED,
				enabledCheckBox.isSelected()
			);
			refreshEnabledState();
			fireSettings();
		});
		volumeSlider.addChangeListener(event ->
		{
			if (updating || isClickSettingsReadOnly())
			{
				return;
			}
			refreshLabel();
			if (!volumeSlider.getValueIsAdjusting())
			{
				persist(
					SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
					HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT,
					volumeSlider.getValue()
				);
				fireSettings();
			}
		});
		minimumXpSpinner.addChangeListener(event ->
		{
			if (updating || isClickSettingsReadOnly())
			{
				return;
			}
			persist(
				SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
				HapticScapeSettingKeys.CLICKER_MINIMUM_XP_GAIN,
				((Number) minimumXpSpinner.getValue()).intValue()
			);
			refreshXpSettings();
		});
		levelUpCheckBox.addActionListener(event ->
		{
			if (updating || levelUpLockBinding.handleAction(event))
			{
				return;
			}
			if (isClickSettingsReadOnly() || levelUpLockBinding.isEditLocked())
			{
				return;
			}
			persist(
				SettingsLockCatalog.CLICKER_LEVEL_UP,
				HapticScapeSettingKeys.CLICKER_LEVEL_UP_ENABLED,
				levelUpCheckBox.isSelected()
			);
			refreshXpSettings();
		});
		milestoneCheckBox.addActionListener(event ->
		{
			if (updating || milestoneLockBinding.handleAction(event))
			{
				return;
			}
			if (isClickSettingsReadOnly() || milestoneLockBinding.isEditLocked())
			{
				return;
			}
			persist(
				SettingsLockCatalog.CLICKER_MILESTONE,
				HapticScapeSettingKeys.CLICKER_MILESTONE_ENABLED,
				milestoneCheckBox.isSelected()
			);
			refreshXpSettings();
		});
		level99CheckBox.addActionListener(event ->
		{
			if (updating || level99LockBinding.handleAction(event))
			{
				return;
			}
			if (isClickSettingsReadOnly() || level99LockBinding.isEditLocked())
			{
				return;
			}
			persist(
				SettingsLockCatalog.CLICKER_LEVEL_99,
				HapticScapeSettingKeys.CLICKER_LEVEL_99_ENABLED,
				level99CheckBox.isSelected()
			);
			refreshXpSettings();
		});
		testButton.addActionListener(event -> testAction.run());
	}

	private void refreshLabel()
	{
		volumeValue.setText(volumeSlider.getValue() + "%");
	}

	private void refreshEnabledState()
	{
		boolean editable = !remoteReadOnly;
		clickSettingsBlockHeader.refresh();
		boolean blockEditable = editable && !clickSettingsBlockHeader.isEditLocked();
		boolean enabled = enabledCheckBox.isSelected();
		enabledLockBinding.refresh();
		levelUpLockBinding.refresh();
		milestoneLockBinding.refresh();
		level99LockBinding.refresh();
		enabledCheckBox.setEnabled(blockEditable && !enabledLockBinding.isEditLocked());
		volumeSlider.setEnabled(blockEditable && enabled);
		minimumXpSpinner.setEnabled(blockEditable && enabled);
		levelUpCheckBox.setEnabled(blockEditable && enabled && !levelUpLockBinding.isEditLocked());
		milestoneCheckBox.setEnabled(
			blockEditable && enabled && !milestoneLockBinding.isEditLocked()
		);
		level99CheckBox.setEnabled(blockEditable && enabled && !level99LockBinding.isEditLocked());
		phraseRulesPanel.setClickerEnabled(enabled);
		phraseRulesPanel.setRemoteReadOnly(remoteReadOnly);
		testButton.setEnabled(
			previewAllowed && editable && enabled && volumeSlider.getValue() > 0
		);
	}

	private void fireSettings()
	{
		settings = new ClickerSettings(
			enabledCheckBox.isSelected(),
			volumeSlider.getValue()
		);
		settingsListener.accept(settings);
		refreshEnabledState();
	}

	private boolean isClickSettingsReadOnly()
	{
		return remoteReadOnly || clickSettingsBlockHeader.isEditLocked();
	}

	private void refreshXpSettings()
	{
		xpSettings = new ClickerXpSettings(
			((Number) minimumXpSpinner.getValue()).intValue(),
			levelUpCheckBox.isSelected(),
			milestoneCheckBox.isSelected(),
			level99CheckBox.isSelected()
		);
	}

	private void persist(String key, Object value)
	{
		settingsSink.set(key, value);
	}

	private void persist(SettingsLockTarget target, String key, Object value)
	{
		settingsSink.set(target, key, value);
	}

	private void reflow(
		Component output,
		Component xp,
		Component phrases)
	{
		int desired = layoutModeForWidth(getWidth());
		if (desired == layoutMode)
		{
			return;
		}
		layoutMode = desired;
		layoutPanel.removeAll();
		if (layoutMode == 3)
		{
			addSection(output, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(xp, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(phrases, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addRemainder(3, 0);
		}
		else if (layoutMode == 2)
		{
			addSection(output, 0, 0, 1, 1, 0.48, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(xp, 1, 0, 1, 1, 0.52, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(phrases, 0, 1, 2, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		else
		{
			addSection(output, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(xp, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(phrases, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		layoutPanel.revalidate();
		layoutPanel.repaint();
	}

	static int layoutModeForWidth(int width)
	{
		return width >= WIDE_BREAKPOINT ? 3 : width >= MEDIUM_BREAKPOINT ? 2 : 1;
	}

	private void addSection(
		Component component,
		int x,
		int y,
		int width,
		int height,
		double weightX,
		double weightY,
		int fill)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.gridwidth = width;
		constraints.gridheight = height;
		constraints.weightx = weightX;
		constraints.weighty = weightY;
		constraints.fill = fill;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = new Insets(0, 0, 7, 7);
		layoutPanel.add(component, constraints);
	}

	private void addRemainder(int x, int y)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		layoutPanel.add(new JPanel(), constraints);
	}

	private static JPanel host(Component component, int preferredWidth)
	{
		JPanel host = new WidthHintPanel(preferredWidth);
		host.add(component, BorderLayout.NORTH);
		return host;
	}

	/** Width stays orderly on a large desktop without freezing dynamic height. */
	private static final class WidthHintPanel extends JPanel
	{
		private final int preferredWidth;

		private WidthHintPanel(int preferredWidth)
		{
			super(new BorderLayout());
			this.preferredWidth = preferredWidth;
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension preferred = super.getPreferredSize();
			return new Dimension(Math.max(preferredWidth, preferred.width), preferred.height);
		}
	}

	private static LockableCheckBoxBinding binding(
		JCheckBox checkBox,
		SettingsLockTarget target,
		SettingsLockDraft lockDraft,
		SettingsLockService lockService,
		RemoteSessionManager sessionManager,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled,
		BooleanSupplier authoritativeValue)
	{
		return new LockableCheckBoxBinding(
			checkBox,
			target,
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled,
			authoritativeValue
		);
	}

	private static JPanel row(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return row;
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
