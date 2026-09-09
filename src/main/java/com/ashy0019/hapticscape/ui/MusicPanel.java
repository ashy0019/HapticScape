package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.music.MusicResponse;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.MusicSyncSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

final class MusicPanel extends JPanel
{
	private final SettingsChangeSink settingsSink;
	private final Consumer<MusicSyncSettings> settingsListener;
	private final JToggleButton enabledButton = new JToggleButton();
	private final JComboBox<MusicResponse> responseComboBox =
		new JComboBox<>(MusicResponse.values());
	private final JSlider sensitivitySlider = new JSlider(25, 200);
	private final JSlider minimumSlider = new JSlider(0, 100);
	private final JSlider maximumSlider = new JSlider(0, 100);
	private final JLabel sensitivityValue = new JLabel();
	private final JLabel minimumValue = new JLabel();
	private final JLabel maximumValue = new JLabel();
	private final JLabel rangeValue = new JLabel();
	private final JLabel responseHint = new JLabel();
	private final JLabel captureStateLabel = new JLabel("Off");
	private final JLabel statusLabel = new JLabel("Music sync is off");
	private final JProgressBar outputMeter = new JProgressBar(0, 100);
	private boolean updating;
	private boolean remoteReadOnly;
	private MusicSyncSnapshot.State displayedState = MusicSyncSnapshot.State.DISABLED;
	private String displayedMessage = "Music sync is off";
	private int displayedLevel;

	MusicPanel(
		HapticScapeSettingsSource config,
		SettingsChangeSink settingsSink,
		Consumer<MusicSyncSettings> settingsListener)
	{
		this.settingsSink = settingsSink;
		this.settingsListener = settingsListener;
		setName("musicSyncWorkspace");
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		enabledButton.setName("musicSyncToggle");
		enabledButton.setSelected(config.musicSyncEnabled());
		responseComboBox.setName("musicResponse");
		responseComboBox.setSelectedItem(parseResponse(config.musicResponse()));
		PanelUi.setFixedWidth(responseComboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		sensitivitySlider.setName("musicSensitivity");
		minimumSlider.setName("musicMinimumIntensity");
		maximumSlider.setName("musicMaximumIntensity");
		sensitivitySlider.setValue(clamp(config.musicSensitivityPercent(), 25, 200));
		minimumSlider.setValue(clamp(config.musicMinimumIntensityPercent(), 0, 100));
		maximumSlider.setValue(clamp(config.musicMaximumIntensityPercent(), 0, 100));
		if (minimumSlider.getValue() > maximumSlider.getValue())
		{
			minimumSlider.setValue(maximumSlider.getValue());
		}

		captureStateLabel.setName("musicCaptureState");
		statusLabel.setName("musicCaptureDetail");
		outputMeter.setName("musicOutputMeter");
		outputMeter.setStringPainted(true);
		outputMeter.setString("Output 0%");

		ResponsiveColumnsPanel sections = new ResponsiveColumnsPanel(
			capturePanel(),
			responsePanel(),
			outputPanel()
		);
		sections.setName("musicSyncSections");
		add(sections, BorderLayout.CENTER);

		refreshLabels();
		refreshEnabledState();
		configureListeners();
	}

	MusicSyncSettings getSettings()
	{
		return new MusicSyncSettings(
			enabledButton.isSelected(),
			(MusicResponse) responseComboBox.getSelectedItem(),
			sensitivitySlider.getValue(),
			minimumSlider.getValue(),
			maximumSlider.getValue()
		);
	}

	void applyDisplayedSettings(MusicSyncSettings displayed)
	{
		updating = true;
		try
		{
			enabledButton.setSelected(displayed.isEnabled());
			responseComboBox.setSelectedItem(displayed.getResponse());
			sensitivitySlider.setValue(displayed.getSensitivityPercent());
			minimumSlider.setValue(displayed.getMinimumIntensityPercent());
			maximumSlider.setValue(displayed.getMaximumIntensityPercent());
			refreshLabels();
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

	void disableMusicSync()
	{
		if (!enabledButton.isSelected())
		{
			return;
		}
		enabledButton.setSelected(false);
		persist(HapticScapeSettingKeys.MUSIC_SYNC_ENABLED, false);
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
		if (snapshot.getState() != displayedState)
		{
			displayedState = snapshot.getState();
			captureStateLabel.setText(stateText(displayedState));
			refreshEnabledState();
		}
		if (!snapshot.getMessage().equals(displayedMessage))
		{
			displayedMessage = snapshot.getMessage();
			statusLabel.setText(displayedMessage);
			statusLabel.setToolTipText(displayedMessage);
		}
		if (snapshot.getLevelPercent() != displayedLevel)
		{
			displayedLevel = snapshot.getLevelPercent();
			outputMeter.setValue(displayedLevel);
			outputMeter.setString("Output " + displayedLevel + "%");
		}
	}

	private JPanel capturePanel()
	{
		JPanel panel = verticalSection("Capture", "musicCaptureSection");
		PanelUi.addPreferredHeightComponent(panel, row("State", captureStateLabel));
		JPanel sourceRow = row("Source", new JLabel("Default system output"));
		sourceRow.setToolTipText("Captures the Windows default output device");
		PanelUi.addPreferredHeightComponent(panel, sourceRow);
		panel.add(Box.createVerticalStrut(6));
		PanelUi.addPreferredHeightComponent(panel, outputMeter);

		statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 1, 5, 1));
		PanelUi.addPreferredHeightComponent(panel, statusLabel);
		JPanel buttonRow = new JPanel(new GridLayout(1, 1));
		buttonRow.add(enabledButton);
		PanelUi.addPreferredHeightComponent(panel, buttonRow);

		JLabel privacy = new JLabel("Analyzed locally; audio is never recorded.");
		privacy.setToolTipText("Audio samples remain in memory on this computer");
		privacy.setBorder(BorderFactory.createEmptyBorder(6, 1, 0, 1));
		PanelUi.addPreferredHeightComponent(panel, privacy);
		return panel;
	}

	private JPanel responsePanel()
	{
		JPanel panel = verticalSection("Response", "musicResponseSection");
		PanelUi.addPreferredHeightComponent(panel, row("Feel", responseComboBox));
		responseHint.setBorder(BorderFactory.createEmptyBorder(5, 1, 7, 1));
		PanelUi.addPreferredHeightComponent(panel, responseHint);
		PanelUi.addPreferredHeightComponent(panel, row("Sensitivity", sensitivityValue));
		sensitivitySlider.setToolTipText(
			"Raises or lowers how strongly HapticScape reacts to captured audio"
		);
		PanelUi.addPreferredHeightComponent(panel, sensitivitySlider);
		return panel;
	}

	private JPanel outputPanel()
	{
		JPanel panel = verticalSection("Output range", "musicOutputSection");
		PanelUi.addPreferredHeightComponent(panel, row("Active range", rangeValue));
		panel.add(Box.createVerticalStrut(5));
		PanelUi.addPreferredHeightComponent(panel, row("Minimum", minimumValue));
		minimumSlider.setToolTipText("Lowest non-silent haptic intensity");
		PanelUi.addPreferredHeightComponent(panel, minimumSlider);
		panel.add(Box.createVerticalStrut(5));
		PanelUi.addPreferredHeightComponent(panel, row("Maximum", maximumValue));
		maximumSlider.setToolTipText("Highest haptic intensity music sync may request");
		PanelUi.addPreferredHeightComponent(panel, maximumSlider);
		return panel;
	}

	private void configureListeners()
	{
		enabledButton.addActionListener(event ->
		{
			if (updating || remoteReadOnly)
			{
				return;
			}
			if (displayedState == MusicSyncSnapshot.State.ERROR)
			{
				// Capture is already stopped. Keep the setting enabled so this click
				// retries opening the source instead of requiring Stop, then Start.
				enabledButton.setSelected(true);
			}
			persist(HapticScapeSettingKeys.MUSIC_SYNC_ENABLED, enabledButton.isSelected());
			refreshEnabledState();
			fireSettings();
		});
		responseComboBox.addActionListener(event ->
		{
			refreshResponseHint();
			if (updating || remoteReadOnly)
			{
				return;
			}
			MusicResponse response = (MusicResponse) responseComboBox.getSelectedItem();
			persist(HapticScapeSettingKeys.MUSIC_RESPONSE, response.name());
			fireSettings();
		});
		sensitivitySlider.addChangeListener(event ->
		{
			refreshLabels();
			if (updating || remoteReadOnly)
			{
				return;
			}
			if (!sensitivitySlider.getValueIsAdjusting())
			{
				persist(HapticScapeSettingKeys.MUSIC_SENSITIVITY_PERCENT,
					sensitivitySlider.getValue());
				fireSettings();
			}
		});
		minimumSlider.addChangeListener(event ->
		{
			if (updating || remoteReadOnly)
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
				persist(HapticScapeSettingKeys.MUSIC_MINIMUM_INTENSITY_PERCENT,
					minimumSlider.getValue());
				persist(HapticScapeSettingKeys.MUSIC_MAXIMUM_INTENSITY_PERCENT,
					maximumSlider.getValue());
				fireSettings();
			}
		});
		maximumSlider.addChangeListener(event ->
		{
			if (updating || remoteReadOnly)
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
				persist(HapticScapeSettingKeys.MUSIC_MINIMUM_INTENSITY_PERCENT,
					minimumSlider.getValue());
				persist(HapticScapeSettingKeys.MUSIC_MAXIMUM_INTENSITY_PERCENT,
					maximumSlider.getValue());
				fireSettings();
			}
		});
	}

	private void refreshLabels()
	{
		sensitivityValue.setText(sensitivitySlider.getValue() + "%");
		minimumValue.setText(minimumSlider.getValue() + "%");
		maximumValue.setText(maximumSlider.getValue() + "%");
		rangeValue.setText(minimumSlider.getValue() + "–" + maximumSlider.getValue() + "%");
		refreshResponseHint();
	}

	private void refreshResponseHint()
	{
		MusicResponse response = (MusicResponse) responseComboBox.getSelectedItem();
		if (response == MusicResponse.SMOOTH)
		{
			responseHint.setText("Even movement with gradual changes.");
		}
		else if (response == MusicResponse.PUNCHY)
		{
			responseHint.setText("Fast attacks with pronounced hits.");
		}
		else
		{
			responseHint.setText("Balanced motion with clear rhythm.");
		}
	}

	private void refreshEnabledState()
	{
		boolean editable = !remoteReadOnly;
		enabledButton.setEnabled(editable);
		if (displayedState == MusicSyncSnapshot.State.ERROR && enabledButton.isSelected())
		{
			enabledButton.setText("Retry music sync");
		}
		else
		{
			enabledButton.setText(enabledButton.isSelected()
				? "Stop music sync"
				: "Start music sync");
		}
		responseComboBox.setEnabled(editable);
		sensitivitySlider.setEnabled(editable);
		minimumSlider.setEnabled(editable);
		maximumSlider.setEnabled(editable);
	}

	private void fireSettings()
	{
		settingsListener.accept(getSettings());
	}

	private void persist(String key, Object value)
	{
		settingsSink.set(key, value);
	}

	private static JPanel verticalSection(String title, String name)
	{
		JPanel panel = new JPanel();
		panel.setName(name);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.setAlignmentY(Component.TOP_ALIGNMENT);
		return panel;
	}

	private static JPanel row(String name, Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return row;
	}

	private static String stateText(MusicSyncSnapshot.State state)
	{
		switch (state)
		{
			case STARTING:
				return "Starting";
			case RUNNING:
				return "Listening";
			case ERROR:
				return "Needs attention";
			case DISABLED:
			default:
				return "Off";
		}
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
