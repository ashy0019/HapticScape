package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.ConnectionState;
import com.ashy0019.hapticscape.device.DeviceInfo;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public final class HapticScapePanel extends PluginPanel
{
	private final JLabel statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);
	private final DefaultListModel<DeviceInfo> deviceModel = new DefaultListModel<>();
	private final JButton connectButton = new JButton("Connect");
	private final JButton disconnectButton = new JButton("Disconnect");
	private final JButton testButton = new JButton("Test pulse");
	private final JButton stopButton = new JButton("Stop now");
	private final JLabel intensityValueLabel = new JLabel();
	private final JSlider intensitySlider;
	private final JSpinner pulseDurationSpinner;
	private volatile int intensityPercent;
	private volatile int pulseDurationMillis;

	public HapticScapePanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Runnable connectAction,
		Runnable disconnectAction,
		Runnable testAction,
		Runnable stopAction)
	{
		super(false);
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

		intensityPercent = clamp(config.intensityPercent(), 0, 100);
		pulseDurationMillis = clamp(config.pulseDurationMillis(), 50, 10_000);

		intensitySlider = new JSlider(0, 100, intensityPercent);
		pulseDurationSpinner = new JSpinner(new SpinnerNumberModel(
			pulseDurationMillis,
			50,
			10_000,
			50));

		intensityValueLabel.setText(intensitySlider.getValue() + "%");
		intensitySlider.addChangeListener(event ->
		{
			intensityPercent = intensitySlider.getValue();
			intensityValueLabel.setText(intensityPercent + "%");
			if (!intensitySlider.getValueIsAdjusting())
			{
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.INTENSITY_PERCENT_KEY,
					intensityPercent);
			}
		});
		pulseDurationSpinner.addChangeListener(event ->
		{
			pulseDurationMillis = ((Number) pulseDurationSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.PULSE_DURATION_MILLIS_KEY,
				pulseDurationMillis);
		});

		JPanel settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Feedback"));

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		settingsPanel.add(intensityHeader);
		settingsPanel.add(intensitySlider);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel("Pulse duration (ms)"), BorderLayout.CENTER);
		durationRow.add(pulseDurationSpinner, BorderLayout.EAST);
		settingsPanel.add(durationRow);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(statusLabel);
		topPanel.add(settingsPanel);
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

	public int getPulseDurationMillis()
	{
		return pulseDurationMillis;
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
		stopButton.setEnabled(connected);
	}
}
