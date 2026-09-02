package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.music.MusicResponse;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.MusicSyncSnapshot;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

final class MusicPanel extends JPanel
{
	private final ConfigManager configManager;
	private final Consumer<MusicSyncSettings> settingsListener;
	private final JCheckBox enabledCheckBox = new JCheckBox("Sync to system audio");
	private final JComboBox<MusicResponse> responseComboBox =
		new JComboBox<>(MusicResponse.values());
	private final JSlider sensitivitySlider = new JSlider(25, 200);
	private final JSlider minimumSlider = new JSlider(0, 100);
	private final JSlider maximumSlider = new JSlider(0, 100);
	private final JLabel sensitivityValue = new JLabel();
	private final JLabel minimumValue = new JLabel();
	private final JLabel maximumValue = new JLabel();
	private final JLabel statusLabel = new JLabel("Music sync is off");
	private final JProgressBar outputMeter = new JProgressBar(0, 100);
	private boolean updating;

	MusicPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Consumer<MusicSyncSettings> settingsListener)
	{
		this.configManager = configManager;
		this.settingsListener = settingsListener;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(5, 4, 4, 4));

		enabledCheckBox.setSelected(config.musicSyncEnabled());
		responseComboBox.setSelectedItem(parseResponse(config.musicResponse()));
		PanelUi.setFixedWidth(responseComboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		sensitivitySlider.setValue(clamp(config.musicSensitivityPercent(), 25, 200));
		minimumSlider.setValue(clamp(config.musicMinimumIntensityPercent(), 0, 100));
		maximumSlider.setValue(clamp(config.musicMaximumIntensityPercent(), 0, 100));
		if (minimumSlider.getValue() > maximumSlider.getValue())
		{
			minimumSlider.setValue(maximumSlider.getValue());
		}

		PanelUi.addVerticalComponent(this, enabledCheckBox);
		PanelUi.addVerticalComponent(this, row("Response", responseComboBox));
		addSlider("Sensitivity", sensitivitySlider, sensitivityValue);
		addSlider("Minimum", minimumSlider, minimumValue);
		addSlider("Maximum", maximumSlider, maximumValue);

		outputMeter.setStringPainted(true);
		outputMeter.setString("Output 0%");
		PanelUi.addVerticalComponent(this, outputMeter);
		PanelUi.addVerticalComponent(this, statusLabel);
		JLabel privacy = new JLabel("FFT stays local; no audio is recorded.");
		privacy.setToolTipText("Analyzes the Windows output mix in memory only");
		PanelUi.addVerticalComponent(this, privacy);

		refreshLabels();
		refreshEnabledState();
		configureListeners();
	}

	MusicSyncSettings getSettings()
	{
		return new MusicSyncSettings(
			enabledCheckBox.isSelected(),
			(MusicResponse) responseComboBox.getSelectedItem(),
			sensitivitySlider.getValue(),
			minimumSlider.getValue(),
			maximumSlider.getValue()
		);
	}

	void disableMusicSync()
	{
		if (!enabledCheckBox.isSelected())
		{
			return;
		}
		enabledCheckBox.setSelected(false);
		persist(HapticScapeConfig.MUSIC_SYNC_ENABLED_KEY, false);
		refreshEnabledState();
		settingsListener.accept(getSettings());
	}

	void updateSnapshot(MusicSyncSnapshot snapshot)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> updateSnapshot(snapshot));
			return;
		}
		statusLabel.setText(snapshot.getMessage());
		outputMeter.setValue(snapshot.getLevelPercent());
		outputMeter.setString("Output " + snapshot.getLevelPercent() + "%");
	}

	private void configureListeners()
	{
		enabledCheckBox.addActionListener(event ->
		{
			persist(HapticScapeConfig.MUSIC_SYNC_ENABLED_KEY, enabledCheckBox.isSelected());
			refreshEnabledState();
			fireSettings();
		});
		responseComboBox.addActionListener(event ->
		{
			MusicResponse response = (MusicResponse) responseComboBox.getSelectedItem();
			persist(HapticScapeConfig.MUSIC_RESPONSE_KEY, response.name());
			fireSettings();
		});
		sensitivitySlider.addChangeListener(event ->
		{
			refreshLabels();
			if (!sensitivitySlider.getValueIsAdjusting())
			{
				persist(HapticScapeConfig.MUSIC_SENSITIVITY_PERCENT_KEY,
					sensitivitySlider.getValue());
				fireSettings();
			}
		});
		minimumSlider.addChangeListener(event ->
		{
			if (updating)
			{
				return;
			}
			if (minimumSlider.getValue() > maximumSlider.getValue())
			{
				updating = true;
				maximumSlider.setValue(minimumSlider.getValue());
				updating = false;
			}
			refreshLabels();
			if (!minimumSlider.getValueIsAdjusting())
			{
				persist(HapticScapeConfig.MUSIC_MINIMUM_INTENSITY_PERCENT_KEY,
					minimumSlider.getValue());
				persist(HapticScapeConfig.MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY,
					maximumSlider.getValue());
				fireSettings();
			}
		});
		maximumSlider.addChangeListener(event ->
		{
			if (updating)
			{
				return;
			}
			if (maximumSlider.getValue() < minimumSlider.getValue())
			{
				updating = true;
				minimumSlider.setValue(maximumSlider.getValue());
				updating = false;
			}
			refreshLabels();
			if (!maximumSlider.getValueIsAdjusting())
			{
				persist(HapticScapeConfig.MUSIC_MINIMUM_INTENSITY_PERCENT_KEY,
					minimumSlider.getValue());
				persist(HapticScapeConfig.MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY,
					maximumSlider.getValue());
				fireSettings();
			}
		});
	}

	private void addSlider(String name, JSlider slider, JLabel value)
	{
		PanelUi.addVerticalComponent(this, row(name, value));
		PanelUi.addVerticalComponent(this, slider);
	}

	private static JPanel row(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return row;
	}

	private void refreshLabels()
	{
		sensitivityValue.setText(sensitivitySlider.getValue() + "%");
		minimumValue.setText(minimumSlider.getValue() + "%");
		maximumValue.setText(maximumSlider.getValue() + "%");
	}

	private void refreshEnabledState()
	{
		boolean enabled = enabledCheckBox.isSelected();
		responseComboBox.setEnabled(enabled);
		sensitivitySlider.setEnabled(enabled);
		minimumSlider.setEnabled(enabled);
		maximumSlider.setEnabled(enabled);
	}

	private void fireSettings()
	{
		settingsListener.accept(getSettings());
	}

	private void persist(String key, Object value)
	{
		configManager.setConfiguration(HapticScapeConfig.GROUP, key, value);
	}

	private static MusicResponse parseResponse(String value)
	{
		try
		{
			return MusicResponse.valueOf(value);
		}
		catch (IllegalArgumentException | NullPointerException ignored)
		{
			return MusicResponse.RHYTHMIC;
		}
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
