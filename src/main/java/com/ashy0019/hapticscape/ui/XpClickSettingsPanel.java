package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.util.function.BooleanSupplier;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Global XP click policy; per-skill overrides live in the skill profile editor. */
final class XpClickSettingsPanel extends JPanel
{
	private final SettingsChangeSink settingsSink;
	private final Runnable settingsChangedAction;
	private final SettingsLockService lockService;
	private final BooleanSupplier editingRemoteSubject;
	private final JSpinner minimumXpSpinner = new JSpinner(new SpinnerNumberModel(
		ClickerXpSettings.MINIMUM_XP_GAIN,
		ClickerXpSettings.MINIMUM_XP_GAIN,
		ClickerXpSettings.MAXIMUM_XP_GAIN,
		1
	));
	private final JComboBox<ClickSequence> xpSequenceComboBox =
		ClickSequenceControls.enabledOnly();
	private final JComboBox<ClickSequence> levelUpComboBox =
		ClickSequenceControls.override();
	private final JComboBox<ClickSequence> milestoneComboBox =
		ClickSequenceControls.override();
	private final LockableSectionHeader blockHeader;
	private volatile ClickerXpSettings settings;
	private boolean updating;
	private boolean remoteReadOnly;

	XpClickSettingsPanel(
		HapticScapeSettingsSource config,
		SettingsChangeSink settingsSink,
		Runnable settingsChangedAction,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.settingsSink = settingsSink;
		this.settingsChangedAction = settingsChangedAction == null ? () -> { } : settingsChangedAction;
		this.lockService = lockService;
		this.editingRemoteSubject = editingRemoteSubject;
		settings = fromConfig(config);
		blockHeader = new LockableSectionHeader(
			"",
			() -> SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled
		);

		setName("xpClickSettings");
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(PanelUi.createSectionBorder("XP clicks"));
		PanelUi.addPreferredHeightComponent(this, blockHeader);
		PanelUi.setFixedWidth(minimumXpSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		PanelUi.addPreferredHeightComponent(this, row("Global XP click threshold", minimumXpSpinner));
		PanelUi.addPreferredHeightComponent(this, row("XP gain", xpSequenceComboBox));
		PanelUi.addPreferredHeightComponent(this, row("Level-up", levelUpComboBox));
		PanelUi.addPreferredHeightComponent(this, row("Milestone", milestoneComboBox));

		applyControls(settings);
		configureListeners();
		refreshEnabledState();
	}

	ClickerXpSettings getSettings()
	{
		return settings;
	}

	void applyDisplayedSettings(ClickerXpSettings displayedSettings)
	{
		settings = displayedSettings;
		updating = true;
		try
		{
			applyControls(displayedSettings);
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
		refreshEnabledState();
	}

	private void configureListeners()
	{
		minimumXpSpinner.addChangeListener(event ->
		{
			if (updating || isBlockReadOnly())
			{
				return;
			}
			persist(
				SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
				HapticScapeSettingKeys.CLICKER_MINIMUM_XP_GAIN,
				((Number) minimumXpSpinner.getValue()).intValue()
			);
			refreshSettingsAndNotify();
		});
		xpSequenceComboBox.addActionListener(event ->
		{
			if (updating || isBlockReadOnly())
			{
				return;
			}
			ClickSequence selected = selected(xpSequenceComboBox, ClickSequence.ONE);
			persist(
				SettingsLockCatalog.CLICK_SETTINGS_BLOCK,
				HapticScapeSettingKeys.CLICKER_XP_SEQUENCE,
				selected.toConfigValue()
			);
			refreshSettingsAndNotify();
		});
		levelUpComboBox.addActionListener(event ->
		{
			if (updating || isTargetReadOnly(SettingsLockCatalog.CLICKER_LEVEL_UP))
			{
				return;
			}
			ClickSequence selected = selected(levelUpComboBox, ClickSequence.NONE);
			persist(
				SettingsLockCatalog.CLICKER_LEVEL_UP,
				HapticScapeSettingKeys.CLICKER_LEVEL_UP_SEQUENCE,
				selected.toConfigValue()
			);
			refreshSettingsAndNotify();
		});
		milestoneComboBox.addActionListener(event ->
		{
			if (updating || isTargetReadOnly(SettingsLockCatalog.CLICKER_MILESTONE))
			{
				return;
			}
			ClickSequence selected = selected(milestoneComboBox, ClickSequence.NONE);
			persist(
				SettingsLockCatalog.CLICKER_MILESTONE,
				HapticScapeSettingKeys.CLICKER_MILESTONE_SEQUENCE,
				selected.toConfigValue()
			);
			refreshSettingsAndNotify();
		});
	}

	private void applyControls(ClickerXpSettings displayedSettings)
	{
		minimumXpSpinner.setValue(clamp(
			displayedSettings.getMinimumXpGain(),
			ClickerXpSettings.MINIMUM_XP_GAIN,
			ClickerXpSettings.MAXIMUM_XP_GAIN
		));
		xpSequenceComboBox.setSelectedItem(normalizeEnabled(
			displayedSettings.getXpGainSequence()
		));
		levelUpComboBox.setSelectedItem(displayedSettings.getLevelUpOverride());
		milestoneComboBox.setSelectedItem(displayedSettings.getMilestoneOverride());
	}

	private void refreshSettings()
	{
		settings = new ClickerXpSettings(
			((Number) minimumXpSpinner.getValue()).intValue(),
			selected(xpSequenceComboBox, ClickSequence.ONE),
			selected(levelUpComboBox, ClickSequence.NONE),
			selected(milestoneComboBox, ClickSequence.NONE)
		);
	}

	private void refreshSettingsAndNotify()
	{
		refreshSettings();
		settingsChangedAction.run();
	}

	private void refreshEnabledState()
	{
		blockHeader.refresh();
		boolean blockEditable = !remoteReadOnly && !blockHeader.isEditLocked();
		minimumXpSpinner.setEnabled(blockEditable);
		xpSequenceComboBox.setEnabled(blockEditable);
		levelUpComboBox.setEnabled(
			blockEditable && !isLocallyLocked(SettingsLockCatalog.CLICKER_LEVEL_UP)
		);
		milestoneComboBox.setEnabled(
			blockEditable && !isLocallyLocked(SettingsLockCatalog.CLICKER_MILESTONE)
		);
	}

	private boolean isBlockReadOnly()
	{
		return remoteReadOnly || blockHeader.isEditLocked();
	}

	private boolean isTargetReadOnly(SettingsLockTarget target)
	{
		return isBlockReadOnly() || isLocallyLocked(target);
	}

	private boolean isLocallyLocked(SettingsLockTarget target)
	{
		return !editingRemoteSubject.getAsBoolean() && lockService.isLocked(target);
	}

	private void persist(SettingsLockTarget target, String key, Object value)
	{
		settingsSink.set(target, key, value);
	}

	private static ClickerXpSettings fromConfig(HapticScapeSettingsSource config)
	{
		return new ClickerXpSettings(
			config.clickerMinimumXpGain(),
			ClickSequence.fromConfigValue(config.clickerXpSequence(), ClickSequence.ONE),
			ClickSequence.fromConfigValue(config.clickerLevelUpSequence(), ClickSequence.ONE),
			ClickSequence.fromConfigValue(config.clickerMilestoneSequence(), ClickSequence.ONE)
		);
	}

	private static ClickSequence normalizeEnabled(ClickSequence sequence)
	{
		return sequence != null && sequence.isEnabled() ? sequence : ClickSequence.ONE;
	}

	private static ClickSequence selected(
		JComboBox<ClickSequence> comboBox,
		ClickSequence fallback)
	{
		Object selected = comboBox.getSelectedItem();
		return selected instanceof ClickSequence ? (ClickSequence) selected : fallback;
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
