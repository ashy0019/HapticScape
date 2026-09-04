package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import java.awt.BorderLayout;
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
	private final SettingsChangeSink settingsSink;
	private final Consumer<ClickerSettings> settingsListener;
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
	private boolean updating;
	private boolean remoteReadOnly;
	private boolean previewAllowed = true;

	ClickerPanel(
		HapticScapeConfig config,
		SettingsChangeSink settingsSink,
		Consumer<ClickerSettings> settingsListener,
		Runnable testAction)
	{
		this.settingsSink = settingsSink;
		this.settingsListener = settingsListener;
		phraseRulesPanel = new ClickerPhraseRulesPanel(config, settingsSink);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(5, 4, 4, 4));

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

		PanelUi.addVerticalComponent(this, enabledCheckBox);
		PanelUi.addVerticalComponent(this, row("Volume", volumeValue));
		PanelUi.addVerticalComponent(this, volumeSlider);

		JPanel xpSettings = new JPanel();
		xpSettings.setLayout(new BoxLayout(xpSettings, BoxLayout.Y_AXIS));
		xpSettings.setBorder(BorderFactory.createTitledBorder("XP clicks"));
		JLabel skillHint = new JLabel("Select skills under Skills → Clicker.");
		skillHint.setToolTipText("The Skills tab stores separate Haptics and Clicker selections");
		PanelUi.addVerticalComponent(xpSettings, skillHint);
		PanelUi.addVerticalComponent(
			xpSettings,
			row("Minimum XP gain", minimumXpSpinner)
		);
		PanelUi.addVerticalComponent(xpSettings, levelUpCheckBox);
		PanelUi.addVerticalComponent(xpSettings, milestoneCheckBox);
		PanelUi.addVerticalComponent(xpSettings, level99CheckBox);
		PanelUi.addVerticalComponent(this, xpSettings);
		PanelUi.addVerticalComponent(this, phraseRulesPanel);
		PanelUi.addVerticalComponent(this, testButton);

		JLabel description = new JLabel("Works without Intiface or a connected device.");
		description.setToolTipText("Click playback is independent of haptic feedback");
		PanelUi.addVerticalComponent(this, description);

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
			if (updating || remoteReadOnly)
			{
				return;
			}
			persist(HapticScapeConfig.CLICKER_ENABLED_KEY, enabledCheckBox.isSelected());
			refreshEnabledState();
			fireSettings();
		});
		volumeSlider.addChangeListener(event ->
		{
			if (updating || remoteReadOnly)
			{
				return;
			}
			refreshLabel();
			if (!volumeSlider.getValueIsAdjusting())
			{
				persist(
					HapticScapeConfig.CLICKER_VOLUME_PERCENT_KEY,
					volumeSlider.getValue()
				);
				fireSettings();
			}
		});
		minimumXpSpinner.addChangeListener(event ->
		{
			if (updating || remoteReadOnly)
			{
				return;
			}
			persist(
				HapticScapeConfig.CLICKER_MINIMUM_XP_GAIN_KEY,
				((Number) minimumXpSpinner.getValue()).intValue()
			);
			refreshXpSettings();
		});
		levelUpCheckBox.addActionListener(event ->
		{
			if (updating || remoteReadOnly)
			{
				return;
			}
			persist(
				HapticScapeConfig.CLICKER_LEVEL_UP_ENABLED_KEY,
				levelUpCheckBox.isSelected()
			);
			refreshXpSettings();
		});
		milestoneCheckBox.addActionListener(event ->
		{
			if (updating || remoteReadOnly)
			{
				return;
			}
			persist(
				HapticScapeConfig.CLICKER_MILESTONE_ENABLED_KEY,
				milestoneCheckBox.isSelected()
			);
			refreshXpSettings();
		});
		level99CheckBox.addActionListener(event ->
		{
			if (updating || remoteReadOnly)
			{
				return;
			}
			persist(
				HapticScapeConfig.CLICKER_LEVEL_99_ENABLED_KEY,
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
		boolean enabled = enabledCheckBox.isSelected();
		enabledCheckBox.setEnabled(editable);
		volumeSlider.setEnabled(editable && enabled);
		minimumXpSpinner.setEnabled(editable && enabled);
		levelUpCheckBox.setEnabled(editable && enabled);
		milestoneCheckBox.setEnabled(editable && enabled);
		level99CheckBox.setEnabled(editable && enabled);
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
