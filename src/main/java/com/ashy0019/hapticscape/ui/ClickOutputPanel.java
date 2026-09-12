package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.remote.SettingsStore;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Local-authority click output controls.
 *
 * <p>Remote Play may request click feedback for events, but the person running
 * this machine always owns whether clicks are audible and how loud they are.</p>
 */
final class ClickOutputPanel extends JPanel
{
	private final SettingsStore settingsStore;
	private final Consumer<ClickerSettings> settingsListener;
	private final JCheckBox enabledCheckBox = new JCheckBox("Enable click feedback");
	private final JSlider volumeSlider = new JSlider(
		ClickerSettings.MINIMUM_VOLUME_PERCENT,
		ClickerSettings.MAXIMUM_VOLUME_PERCENT
	);
	private final JLabel volumeValue = new JLabel();
	private final JButton testButton = new JButton("Test click");
	private volatile ClickerSettings settings;
	private boolean previewAllowed = true;

	ClickOutputPanel(
		HapticScapeSettingsSource config,
		SettingsStore settingsStore,
		Consumer<ClickerSettings> settingsListener,
		Runnable testAction)
	{
		this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
		this.settingsListener = Objects.requireNonNull(settingsListener, "settingsListener");
		Objects.requireNonNull(testAction, "testAction");

		settings = new ClickerSettings(
			config.clickerEnabled(),
			config.clickerVolumePercent()
		);
		enabledCheckBox.setSelected(settings.isEnabled());
		volumeSlider.setValue(clamp(
			settings.getVolumePercent(),
			ClickerSettings.MINIMUM_VOLUME_PERCENT,
			ClickerSettings.MAXIMUM_VOLUME_PERCENT
		));

		setName("clickOutputSettings");
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(PanelUi.createSectionBorder("Click output"));
		enabledCheckBox.setToolTipText(
			"Local master switch. Remote Play cannot change this setting."
		);
		volumeSlider.setToolTipText(
			"Local click volume. Remote Play cannot change this setting."
		);
		PanelUi.addPreferredHeightComponent(this, enabledCheckBox);
		PanelUi.addPreferredHeightComponent(this, row("Volume", volumeValue));
		PanelUi.addPreferredHeightComponent(this, volumeSlider);
		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testButton, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(this, testRow);
		JLabel authorityHint = new JLabel("Always controlled on this computer.");
		authorityHint.setEnabled(false);
		authorityHint.setToolTipText(
			"Controllers can choose which events request clicks, but cannot enable or raise local click output."
		);
		PanelUi.addPreferredHeightComponent(this, authorityHint);

		refreshLabel();
		refreshEnabledState();

		enabledCheckBox.addActionListener(event ->
		{
			settingsStore.set(
				HapticScapeSettingKeys.CLICKER_ENABLED,
				enabledCheckBox.isSelected()
			);
			fireSettings();
		});
		volumeSlider.addChangeListener(event ->
		{
			refreshLabel();
			if (!volumeSlider.getValueIsAdjusting())
			{
				settingsStore.set(
					HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT,
					volumeSlider.getValue()
				);
				fireSettings();
			}
		});
		testButton.addActionListener(event -> testAction.run());
	}

	ClickerSettings getSettings()
	{
		return settings;
	}

	void setPreviewAllowed(boolean previewAllowed)
	{
		this.previewAllowed = previewAllowed;
		refreshEnabledState();
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

	private void refreshLabel()
	{
		volumeValue.setText(volumeSlider.getValue() + "%");
	}

	private void refreshEnabledState()
	{
		boolean enabled = enabledCheckBox.isSelected();
		volumeSlider.setEnabled(enabled);
		testButton.setEnabled(
			previewAllowed && enabled && volumeSlider.getValue() > 0
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
