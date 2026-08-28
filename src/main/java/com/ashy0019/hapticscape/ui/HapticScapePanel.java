package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.SkillSelection;
import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.ConnectionState;
import com.ashy0019.hapticscape.device.DeviceInfo;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public final class HapticScapePanel extends PluginPanel
{
	private static final int MAXIMUM_XP_GAIN = 200_000_000;

	private final JLabel statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);
	private final DefaultListModel<DeviceInfo> deviceModel = new DefaultListModel<>();
	private final JButton connectButton = new JButton("Connect");
	private final JButton disconnectButton = new JButton("Disconnect");
	private final JButton testButton = new JButton("Test pulse");
	private final JButton stopButton = new JButton("Stop now");
	private final JLabel intensityValueLabel = new JLabel();
	private final JLabel enabledSkillsValueLabel = new JLabel();
	private final Map<Skill, JCheckBox> skillCheckBoxes = new EnumMap<>(Skill.class);
	private final ConfigManager configManager;
	private final JSlider intensitySlider;
	private final JSpinner minimumXpGainSpinner;
	private final JSpinner pulseDurationSpinner;
	private volatile int intensityPercent;
	private volatile int minimumXpGain;
	private volatile int pulseDurationMillis;
	private volatile SkillSelection skillSelection;
	private boolean updatingSkillCheckBoxes;

	public HapticScapePanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Runnable connectAction,
		Runnable disconnectAction,
		Runnable testAction,
		Runnable stopAction)
	{
		super(false);
		this.configManager = configManager;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

		intensityPercent = clamp(config.intensityPercent(), 0, 100);
		minimumXpGain = clamp(config.minimumXpGain(), 1, MAXIMUM_XP_GAIN);
		pulseDurationMillis = clamp(config.pulseDurationMillis(), 50, 10_000);
		skillSelection = SkillSelection.fromConfigValue(config.disabledSkills());

		intensitySlider = new JSlider(0, 100, intensityPercent);
		minimumXpGainSpinner = new JSpinner(new SpinnerNumberModel(
			minimumXpGain,
			1,
			MAXIMUM_XP_GAIN,
			1));
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
		minimumXpGainSpinner.addChangeListener(event ->
		{
			minimumXpGain = ((Number) minimumXpGainSpinner.getValue()).intValue();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.MINIMUM_XP_GAIN_KEY,
				minimumXpGain);
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

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		thresholdRow.add(minimumXpGainSpinner, BorderLayout.EAST);
		settingsPanel.add(thresholdRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		settingsPanel.add(intensityHeader);
		settingsPanel.add(intensitySlider);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel("Pulse duration (ms)"), BorderLayout.CENTER);
		durationRow.add(pulseDurationSpinner, BorderLayout.EAST);
		settingsPanel.add(durationRow);

		JPanel skillsPanel = new JPanel(new BorderLayout(0, 4));
		skillsPanel.setBorder(BorderFactory.createTitledBorder("XP skills"));

		JButton allSkillsButton = new JButton("All");
		allSkillsButton.setToolTipText("Enable XP feedback for every skill");
		allSkillsButton.addActionListener(event -> setAllSkillsEnabled(true));

		JButton noSkillsButton = new JButton("None");
		noSkillsButton.setToolTipText("Disable XP feedback for every skill");
		noSkillsButton.addActionListener(event -> setAllSkillsEnabled(false));

		JPanel bulkSkillButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		bulkSkillButtons.add(allSkillsButton);
		bulkSkillButtons.add(noSkillsButton);

		JPanel skillsHeader = new JPanel(new BorderLayout(4, 0));
		skillsHeader.add(enabledSkillsValueLabel, BorderLayout.WEST);
		skillsHeader.add(bulkSkillButtons, BorderLayout.EAST);
		skillsPanel.add(skillsHeader, BorderLayout.NORTH);

		JPanel skillGrid = new JPanel(new GridLayout(0, 2, 4, 2));
		for (Skill skill : SkillSelection.getSelectableSkills())
		{
			JCheckBox skillCheckBox = new JCheckBox(skill.getName(), skillSelection.isEnabled(skill));
			skillCheckBox.addActionListener(event -> setSkillEnabled(skill, skillCheckBox.isSelected()));
			skillCheckBoxes.put(skill, skillCheckBox);
			skillGrid.add(skillCheckBox);
		}

		skillsPanel.add(skillGrid, BorderLayout.CENTER);
		updateEnabledSkillsLabel();

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(statusLabel);
		topPanel.add(settingsPanel);
		topPanel.add(skillsPanel);
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

	public int getMinimumXpGain()
	{
		return minimumXpGain;
	}

	public int getPulseDurationMillis()
	{
		return pulseDurationMillis;
	}

	public boolean isSkillEnabled(Skill skill)
	{
		return skillSelection.isEnabled(skill);
	}

	private void setSkillEnabled(Skill skill, boolean enabled)
	{
		if (updatingSkillCheckBoxes)
		{
			return;
		}

		skillSelection = skillSelection.withEnabled(skill, enabled);
		persistSkillSelection();
		updateEnabledSkillsLabel();
	}

	private void setAllSkillsEnabled(boolean enabled)
	{
		SkillSelection updated = skillSelection.withAllEnabled(enabled);
		skillSelection = updated;

		updatingSkillCheckBoxes = true;
		try
		{
			for (Map.Entry<Skill, JCheckBox> entry : skillCheckBoxes.entrySet())
			{
				entry.getValue().setSelected(updated.isEnabled(entry.getKey()));
			}
		}
		finally
		{
			updatingSkillCheckBoxes = false;
		}

		persistSkillSelection();
		updateEnabledSkillsLabel();
	}

	private void persistSkillSelection()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.DISABLED_SKILLS_KEY,
			skillSelection.toConfigValue()
		);
	}

	private void updateEnabledSkillsLabel()
	{
		enabledSkillsValueLabel.setText(
			skillSelection.getEnabledCount()
				+ "/"
				+ SkillSelection.getSelectableSkills().size()
				+ " enabled"
		);
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
