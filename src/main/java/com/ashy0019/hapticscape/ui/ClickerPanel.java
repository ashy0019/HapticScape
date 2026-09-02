package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import net.runelite.client.config.ConfigManager;

final class ClickerPanel extends JPanel
{
	private final ConfigManager configManager;
	private final Consumer<ClickerSettings> settingsListener;
	private final JCheckBox enabledCheckBox = new JCheckBox("Enable clicker");
	private final JSlider volumeSlider = new JSlider(
		ClickerSettings.MINIMUM_VOLUME_PERCENT,
		ClickerSettings.MAXIMUM_VOLUME_PERCENT
	);
	private final JLabel volumeValue = new JLabel();
	private final JButton testButton = new JButton("Test click");

	ClickerPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Consumer<ClickerSettings> settingsListener,
		Runnable testAction)
	{
		this.configManager = configManager;
		this.settingsListener = settingsListener;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(5, 4, 4, 4));

		enabledCheckBox.setSelected(config.clickerEnabled());
		volumeSlider.setValue(clamp(
			config.clickerVolumePercent(),
			ClickerSettings.MINIMUM_VOLUME_PERCENT,
			ClickerSettings.MAXIMUM_VOLUME_PERCENT
		));

		PanelUi.addVerticalComponent(this, enabledCheckBox);
		PanelUi.addVerticalComponent(this, row("Volume", volumeValue));
		PanelUi.addVerticalComponent(this, volumeSlider);
		PanelUi.addVerticalComponent(this, testButton);

		JLabel description = new JLabel("Works without Intiface or a connected device.");
		description.setToolTipText("Click playback is independent of haptic feedback");
		PanelUi.addVerticalComponent(this, description);

		refreshLabel();
		refreshEnabledState();
		configureListeners(testAction);
	}

	ClickerSettings getSettings()
	{
		return new ClickerSettings(
			enabledCheckBox.isSelected(),
			volumeSlider.getValue()
		);
	}

	private void configureListeners(Runnable testAction)
	{
		enabledCheckBox.addActionListener(event ->
		{
			persist(HapticScapeConfig.CLICKER_ENABLED_KEY, enabledCheckBox.isSelected());
			refreshEnabledState();
			fireSettings();
		});
		volumeSlider.addChangeListener(event ->
		{
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
		testButton.addActionListener(event -> testAction.run());
	}

	private void refreshLabel()
	{
		volumeValue.setText(volumeSlider.getValue() + "%");
	}

	private void refreshEnabledState()
	{
		boolean enabled = enabledCheckBox.isSelected();
		volumeSlider.setEnabled(enabled);
		testButton.setEnabled(enabled && volumeSlider.getValue() > 0);
	}

	private void fireSettings()
	{
		settingsListener.accept(getSettings());
		refreshEnabledState();
	}

	private void persist(String key, Object value)
	{
		configManager.setConfiguration(HapticScapeConfig.GROUP, key, value);
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
