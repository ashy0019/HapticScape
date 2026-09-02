package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.AlertBehavior;
import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.AlertProfile;
import com.ashy0019.hapticscape.AlertProfiles;
import com.ashy0019.hapticscape.AlertTriggerParameter;
import com.ashy0019.hapticscape.AlertTriggerSettings;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.NotificationFeedbackSettings;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.config.ConfigManager;

final class AlertsPanel extends JPanel
{
	private final ConfigManager configManager;
	private final Supplier<CustomPatternLibrary> customPatternsSupplier;

	private final JCheckBox genericEnabledCheckBox =
		new JCheckBox("Generic RuneLite notifications");
	private final JCheckBox respectFocusCheckBox = new JCheckBox("Respect RuneLite focus");
	private final JSlider genericIntensitySlider;
	private final JLabel genericIntensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> genericPatternComboBox;
	private final JSpinner genericDurationSpinner;
	private final JButton testGenericButton = new JButton("Test generic");

	private final JComboBox<AlertCategory> categoryComboBox =
		new JComboBox<>(AlertCategory.values());
	private final JComboBox<AlertBehavior> behaviorComboBox =
		new JComboBox<>(AlertBehavior.values());
	private final JPanel triggerRow = new JPanel(new BorderLayout(8, 0));
	private final JLabel triggerLabel = new JLabel();
	private final JSpinner triggerSpinner = new JSpinner();
	private final JSlider specificIntensitySlider;
	private final JLabel specificIntensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> specificPatternComboBox;
	private final JSpinner specificDurationSpinner;
	private final JButton testSpecificButton = new JButton("Test alert");

	private volatile boolean genericEnabled;
	private volatile boolean respectFocus;
	private volatile int genericIntensityPercent;
	private volatile int genericDurationMillis;
	private volatile HapticPatternSelection genericPattern;
	private volatile AlertProfiles alertProfiles;
	private volatile AlertTriggerSettings triggerSettings;
	private volatile AlertCategory selectedCategory = AlertCategory.DIRECT_MESSAGE;
	private boolean updatingGenericControls;
	private boolean updatingSpecificControls;
	private boolean updatingPatternChoices;
	private boolean connected;

	AlertsPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Supplier<CustomPatternLibrary> customPatternsSupplier,
		Runnable testGenericAction,
		Consumer<AlertCategory> testSpecificAction)
	{
		this.configManager = configManager;
		this.customPatternsSupplier = customPatternsSupplier;
		genericEnabled = config.notificationFeedbackEnabled();
		respectFocus = config.notificationRespectFocus();
		genericIntensityPercent = clamp(
			config.notificationIntensityPercent(),
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT
		);
		genericDurationMillis = clamp(
			config.notificationDurationMillis(),
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS
		);
		genericPattern = HapticPatternSelection.fromConfigValue(
			config.notificationPatternPreset()
		).resolveAgainst(customPatternsSupplier.get());

		String configuredProfiles = config.alertProfiles();
		alertProfiles = AlertProfiles.fromConfigValue(configuredProfiles)
			.replaceMissingCustomPatterns(customPatternsSupplier.get());
		triggerSettings = AlertTriggerSettings.fromConfigValues(
			config.alertTriggerSettings(),
			configuredProfiles
		);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		genericIntensitySlider = new JSlider(
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			genericIntensityPercent
		);
		genericPatternComboBox = PanelUi.createPatternComboBox(customPatternsSupplier);
		genericPatternComboBox.setSelectedItem(genericPattern);
		genericDurationSpinner = new JSpinner(new SpinnerNumberModel(
			genericDurationMillis,
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50
		));
		PanelUi.setFixedWidth(genericDurationSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);

		AlertProfile initialProfile = alertProfiles.get(selectedCategory);
		specificIntensitySlider = new JSlider(
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			initialProfile.getIntensityPercent()
		);
		specificPatternComboBox = PanelUi.createPatternComboBox(customPatternsSupplier);
		specificDurationSpinner = new JSpinner(new SpinnerNumberModel(
			initialProfile.getDurationMillis(),
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50
		));
		PanelUi.setFixedWidth(specificDurationSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		PanelUi.setFixedWidth(triggerSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);

		add(createGenericPanel());
		add(Box.createVerticalStrut(6));
		add(createSpecificPanel());
		configureListeners(testGenericAction, testSpecificAction);

		persistMigratedSettings(configuredProfiles, config.alertTriggerSettings());
		loadSelectedCategory();
		setConnected(false);
	}

	NotificationFeedbackSettings getGenericSettings()
	{
		return new NotificationFeedbackSettings(
			genericEnabled,
			respectFocus,
			genericIntensityPercent,
			genericDurationMillis,
			genericPattern
		);
	}

	AlertProfiles getAlertProfiles()
	{
		return alertProfiles;
	}

	AlertTriggerSettings getTriggerSettings()
	{
		return triggerSettings;
	}

	AlertCategory getSelectedCategory()
	{
		return selectedCategory;
	}

	void applyCustomPatternLibrary(CustomPatternLibrary library)
	{
		HapticPatternSelection resolvedGeneric = genericPattern.resolveAgainst(library);
		if (!resolvedGeneric.equals(genericPattern))
		{
			genericPattern = resolvedGeneric;
			persistGenericPattern();
		}
		AlertProfiles resolvedProfiles = alertProfiles.replaceMissingCustomPatterns(library);
		if (resolvedProfiles != alertProfiles)
		{
			alertProfiles = resolvedProfiles;
			persistProfiles();
		}

		updatingPatternChoices = true;
		try
		{
			PanelUi.setPatternChoices(genericPatternComboBox, genericPattern, library);
			PanelUi.setPatternChoices(
				specificPatternComboBox,
				alertProfiles.get(selectedCategory).getPatternSelection(),
				library
			);
		}
		finally
		{
			updatingPatternChoices = false;
		}
		loadSelectedCategory();
		updateGenericControlState();
	}

	void setConnected(boolean connected)
	{
		this.connected = connected;
		updateGenericControlState();
		updateSpecificControlState();
	}

	private JPanel createGenericPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Generic"));

		genericEnabledCheckBox.setSelected(genericEnabled);
		genericEnabledCheckBox.setToolTipText(
			"Play the Generic profile for unclassified RuneLite notifications"
		);
		respectFocusCheckBox.setSelected(respectFocus);
		respectFocusCheckBox.setToolTipText(
			"Honor RuneLite focus suppression for generic notifications"
		);
		genericIntensityValueLabel.setText(genericIntensityPercent + "%");

		PanelUi.addVerticalComponent(panel, genericEnabledCheckBox);
		PanelUi.addVerticalComponent(panel, respectFocusCheckBox);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(genericIntensityValueLabel, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, intensityHeader);
		PanelUi.addVerticalComponent(panel, genericIntensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(genericPatternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(genericDurationSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, durationRow);

		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testGenericButton, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, testRow);
		return panel;
	}

	private JPanel createSpecificPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Specific alerts"));

		JPanel categoryRow = new JPanel(new BorderLayout());
		categoryComboBox.setToolTipText("Select the alert type to customize");
		categoryRow.add(categoryComboBox, BorderLayout.CENTER);
		PanelUi.addVerticalComponent(panel, categoryRow);

		JPanel behaviorRow = new JPanel(new BorderLayout(8, 0));
		behaviorRow.add(new JLabel("Behavior"), BorderLayout.CENTER);
		behaviorRow.add(behaviorComboBox, BorderLayout.EAST);
		PanelUi.setFixedWidth(behaviorComboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		PanelUi.addVerticalComponent(panel, behaviorRow);

		triggerRow.add(triggerLabel, BorderLayout.CENTER);
		triggerRow.add(triggerSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, triggerRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(specificIntensityValueLabel, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, intensityHeader);
		PanelUi.addVerticalComponent(panel, specificIntensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(specificPatternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(specificDurationSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, durationRow);

		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testSpecificButton, BorderLayout.EAST);
		PanelUi.addVerticalComponent(panel, testRow);
		return panel;
	}

	private void configureListeners(
		Runnable testGenericAction,
		Consumer<AlertCategory> testSpecificAction)
	{
		genericEnabledCheckBox.addActionListener(event ->
		{
			genericEnabled = genericEnabledCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_FEEDBACK_ENABLED_KEY,
				genericEnabled
			);
		});
		respectFocusCheckBox.addActionListener(event ->
		{
			respectFocus = respectFocusCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_RESPECT_FOCUS_KEY,
				respectFocus
			);
		});
		genericIntensitySlider.addChangeListener(event ->
		{
			genericIntensityValueLabel.setText(genericIntensitySlider.getValue() + "%");
			if (!genericIntensitySlider.getValueIsAdjusting() && !updatingGenericControls)
			{
				genericIntensityPercent = genericIntensitySlider.getValue();
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.NOTIFICATION_INTENSITY_PERCENT_KEY,
					genericIntensityPercent
				);
			}
		});
		genericPatternComboBox.addActionListener(event ->
		{
			if (updatingGenericControls || updatingPatternChoices)
			{
				return;
			}
			HapticPatternSelection selected =
				(HapticPatternSelection) genericPatternComboBox.getSelectedItem();
			if (selected != null)
			{
				genericPattern = selected;
				persistGenericPattern();
				updateGenericControlState();
			}
		});
		genericDurationSpinner.addChangeListener(event ->
		{
			if (!updatingGenericControls)
			{
				genericDurationMillis = ((Number) genericDurationSpinner.getValue()).intValue();
				configManager.setConfiguration(
					HapticScapeConfig.GROUP,
					HapticScapeConfig.NOTIFICATION_DURATION_MILLIS_KEY,
					genericDurationMillis
				);
			}
		});
		testGenericButton.addActionListener(event -> testGenericAction.run());

		categoryComboBox.addActionListener(event ->
		{
			AlertCategory category = (AlertCategory) categoryComboBox.getSelectedItem();
			if (category != null)
			{
				selectedCategory = category;
				loadSelectedCategory();
			}
		});
		behaviorComboBox.addActionListener(event ->
		{
			updateSelectedProfile();
			updateSpecificControlState();
		});
		triggerSpinner.addChangeListener(event -> updateSelectedTrigger());
		specificIntensitySlider.addChangeListener(event ->
		{
			specificIntensityValueLabel.setText(specificIntensitySlider.getValue() + "%");
			if (!specificIntensitySlider.getValueIsAdjusting())
			{
				updateSelectedProfile();
			}
		});
		specificPatternComboBox.addActionListener(event ->
		{
			updateSelectedProfile();
			updateSpecificControlState();
		});
		specificDurationSpinner.addChangeListener(event -> updateSelectedProfile());
		testSpecificButton.addActionListener(event ->
			testSpecificAction.accept(selectedCategory));
	}

	private void loadSelectedCategory()
	{
		AlertProfile profile = alertProfiles.get(selectedCategory);
		updatingSpecificControls = true;
		try
		{
			behaviorComboBox.setSelectedItem(profile.getBehavior());
			specificIntensitySlider.setValue(profile.getIntensityPercent());
			specificIntensityValueLabel.setText(profile.getIntensityPercent() + "%");
			specificPatternComboBox.setSelectedItem(profile.getPatternSelection());
			specificDurationSpinner.setValue(profile.getDurationMillis());

			boolean hasTrigger = selectedCategory.hasTriggerParameter();
			triggerRow.setVisible(hasTrigger);
			if (hasTrigger)
			{
				AlertTriggerParameter parameter = selectedCategory.getTriggerParameter();
				triggerLabel.setText(parameter.getLabel());
				triggerSpinner.setModel(new SpinnerNumberModel(
					triggerSettings.get(selectedCategory),
					parameter.getMinimum(),
					parameter.getMaximum(),
					parameter.getStep()
				));
			}
			testSpecificButton.setText(
				"Test " + selectedCategory.getDisplayName().toLowerCase()
			);
		}
		finally
		{
			updatingSpecificControls = false;
		}
		updateSpecificControlState();
		revalidate();
		repaint();
	}

	private void updateSelectedProfile()
	{
		if (updatingSpecificControls || updatingPatternChoices)
		{
			return;
		}
		AlertBehavior behavior = (AlertBehavior) behaviorComboBox.getSelectedItem();
		HapticPatternSelection pattern =
			(HapticPatternSelection) specificPatternComboBox.getSelectedItem();
		if (behavior == null || pattern == null)
		{
			return;
		}

		alertProfiles = alertProfiles.withProfile(
			selectedCategory,
			new AlertProfile(
				behavior,
				specificIntensitySlider.getValue(),
				((Number) specificDurationSpinner.getValue()).intValue(),
				pattern
			)
		);
		persistProfiles();
	}

	private void updateSelectedTrigger()
	{
		if (updatingSpecificControls || !selectedCategory.hasTriggerParameter())
		{
			return;
		}
		triggerSettings = triggerSettings.withValue(
			selectedCategory,
			((Number) triggerSpinner.getValue()).intValue()
		);
		persistTriggerSettings();
	}

	private void updateGenericControlState()
	{
		HapticPatternSelection pattern =
			(HapticPatternSelection) genericPatternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();
		genericPatternComboBox.setEnabled(true);
		genericIntensitySlider.setEnabled(externallyScaled);
		genericIntensityValueLabel.setEnabled(externallyScaled);
		genericDurationSpinner.setEnabled(externallyScaled);
		testGenericButton.setEnabled(connected);
	}

	private void updateSpecificControlState()
	{
		AlertBehavior behavior = (AlertBehavior) behaviorComboBox.getSelectedItem();
		boolean customConfiguration = behavior == AlertBehavior.CUSTOM;
		HapticPatternSelection pattern =
			(HapticPatternSelection) specificPatternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();

		triggerSpinner.setEnabled(selectedCategory.hasTriggerParameter());
		specificPatternComboBox.setEnabled(customConfiguration);
		specificIntensitySlider.setEnabled(customConfiguration && externallyScaled);
		specificIntensityValueLabel.setEnabled(customConfiguration && externallyScaled);
		specificDurationSpinner.setEnabled(customConfiguration && externallyScaled);
		testSpecificButton.setEnabled(connected && behavior != AlertBehavior.OFF);
	}

	private void persistMigratedSettings(
		String configuredProfiles,
		String configuredTriggers)
	{
		if (!alertProfiles.toConfigValue().equals(configuredProfiles))
		{
			persistProfiles();
		}
		if (!triggerSettings.toConfigValue().equals(configuredTriggers))
		{
			persistTriggerSettings();
		}
	}

	private void persistGenericPattern()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.NOTIFICATION_PATTERN_PRESET_KEY,
			genericPattern.toConfigValue()
		);
	}

	private void persistProfiles()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.ALERT_PROFILES_KEY,
			alertProfiles.toConfigValue()
		);
	}

	private void persistTriggerSettings()
	{
		configManager.setConfiguration(
			HapticScapeConfig.GROUP,
			HapticScapeConfig.ALERT_TRIGGER_SETTINGS_KEY,
			triggerSettings.toConfigValue()
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
