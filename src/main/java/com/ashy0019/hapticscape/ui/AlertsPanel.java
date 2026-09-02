package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.AlertBehavior;
import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.AlertProfile;
import com.ashy0019.hapticscape.AlertProfiles;
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
	private final JCheckBox enabledCheckBox = new JCheckBox("Alert haptics");
	private final JCheckBox respectFocusCheckBox = new JCheckBox("Respect RuneLite focus");
	private final JComboBox<AlertCategory> categoryComboBox =
		new JComboBox<>(AlertCategory.values());
	private final JComboBox<AlertBehavior> behaviorComboBox =
		new JComboBox<>(AlertBehavior.values());
	private final JPanel behaviorRow = new JPanel(new BorderLayout(8, 0));
	private final JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
	private final JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(
		20,
		AlertProfile.MINIMUM_THRESHOLD,
		AlertProfile.MAXIMUM_THRESHOLD,
		1
	));
	private final JSlider intensitySlider;
	private final JLabel intensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> patternComboBox;
	private final JSpinner durationSpinner;
	private final JButton testButton = new JButton("Test alert");

	private volatile boolean enabled;
	private volatile boolean respectFocus;
	private volatile int genericIntensityPercent;
	private volatile int genericDurationMillis;
	private volatile HapticPatternSelection genericPattern;
	private volatile AlertProfiles alertProfiles;
	private volatile AlertCategory selectedCategory = AlertCategory.GENERIC_NOTIFICATION;
	private boolean updatingControls;
	private boolean updatingPatternChoices;
	private boolean connected;

	AlertsPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		Supplier<CustomPatternLibrary> customPatternsSupplier,
		Consumer<AlertCategory> testAction)
	{
		this.configManager = configManager;
		this.customPatternsSupplier = customPatternsSupplier;
		enabled = config.notificationFeedbackEnabled();
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
		alertProfiles = AlertProfiles.fromConfigValue(config.alertProfiles())
			.replaceMissingCustomPatterns(customPatternsSupplier.get());

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		enabledCheckBox.setSelected(enabled);
		enabledCheckBox.setToolTipText("Enable generic and categorized alert feedback");
		respectFocusCheckBox.setSelected(respectFocus);
		respectFocusCheckBox.setToolTipText(
			"Honor RuneLite focus suppression for generic RuneLite notifications"
		);

		intensitySlider = new JSlider(
			NotificationFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			NotificationFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			genericIntensityPercent
		);
		intensityValueLabel.setText(genericIntensityPercent + "%");
		patternComboBox = PanelUi.createPatternComboBox(customPatternsSupplier);
		durationSpinner = new JSpinner(new SpinnerNumberModel(
			genericDurationMillis,
			NotificationFeedbackSettings.MINIMUM_DURATION_MILLIS,
			NotificationFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50
		));
		PanelUi.setFixedWidth(durationSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		PanelUi.setFixedWidth(thresholdSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);

		PanelUi.addVerticalComponent(this, enabledCheckBox);
		PanelUi.addVerticalComponent(this, respectFocusCheckBox);
		add(Box.createVerticalStrut(6));

		JPanel categoryRow = new JPanel(new BorderLayout(8, 0));
		categoryRow.add(new JLabel("Alert type"), BorderLayout.CENTER);
		categoryRow.add(categoryComboBox, BorderLayout.EAST);
		PanelUi.setFixedWidth(categoryComboBox, 140);
		PanelUi.addVerticalComponent(this, categoryRow);

		behaviorRow.add(new JLabel("Behavior"), BorderLayout.CENTER);
		behaviorRow.add(behaviorComboBox, BorderLayout.EAST);
		PanelUi.setFixedWidth(behaviorComboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		PanelUi.addVerticalComponent(this, behaviorRow);

		thresholdRow.add(new JLabel("Trigger at or below"), BorderLayout.CENTER);
		thresholdRow.add(thresholdSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, thresholdRow);
		add(Box.createVerticalStrut(6));

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, intensityHeader);
		PanelUi.addVerticalComponent(this, intensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(patternComboBox, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(durationSpinner, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, durationRow);

		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testButton, BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, testRow);

		enabledCheckBox.addActionListener(event ->
		{
			enabled = enabledCheckBox.isSelected();
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_FEEDBACK_ENABLED_KEY,
				enabled
			);
			updateControlState();
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
			updateControlState();
		});
		thresholdSpinner.addChangeListener(event -> updateSelectedProfile());
		intensitySlider.addChangeListener(event ->
		{
			intensityValueLabel.setText(intensitySlider.getValue() + "%");
			if (!intensitySlider.getValueIsAdjusting())
			{
				updateSelectedProfile();
			}
		});
		patternComboBox.addActionListener(event ->
		{
			updateSelectedProfile();
			updateControlState();
		});
		durationSpinner.addChangeListener(event -> updateSelectedProfile());
		testButton.addActionListener(event -> testAction.accept(selectedCategory));
		loadSelectedCategory();
		setConnected(false);
	}

	NotificationFeedbackSettings getGenericSettings()
	{
		return new NotificationFeedbackSettings(
			enabled,
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
			HapticPatternSelection selected = selectedCategory == AlertCategory.GENERIC_NOTIFICATION
				? genericPattern
				: alertProfiles.get(selectedCategory).getPatternSelection();
			PanelUi.setPatternChoices(patternComboBox, selected, library);
		}
		finally
		{
			updatingPatternChoices = false;
		}
		loadSelectedCategory();
	}

	void setConnected(boolean connected)
	{
		this.connected = connected;
		updateControlState();
	}

	private void loadSelectedCategory()
	{
		updatingControls = true;
		try
		{
			boolean generic = selectedCategory == AlertCategory.GENERIC_NOTIFICATION;
			behaviorRow.setVisible(!generic);
			thresholdRow.setVisible(selectedCategory.isThresholdBased());
			if (generic)
			{
				intensitySlider.setValue(genericIntensityPercent);
				intensityValueLabel.setText(genericIntensityPercent + "%");
				patternComboBox.setSelectedItem(genericPattern);
				durationSpinner.setValue(genericDurationMillis);
			}
			else
			{
				AlertProfile profile = alertProfiles.get(selectedCategory);
				behaviorComboBox.setSelectedItem(profile.getBehavior());
				thresholdSpinner.setValue(profile.getThreshold());
				intensitySlider.setValue(profile.getIntensityPercent());
				intensityValueLabel.setText(profile.getIntensityPercent() + "%");
				patternComboBox.setSelectedItem(profile.getPatternSelection());
				durationSpinner.setValue(profile.getDurationMillis());
			}
			testButton.setText("Test " + selectedCategory.getDisplayName().toLowerCase());
		}
		finally
		{
			updatingControls = false;
		}
		updateControlState();
		revalidate();
		repaint();
	}

	private void updateSelectedProfile()
	{
		if (updatingControls || updatingPatternChoices)
		{
			return;
		}
		HapticPatternSelection pattern =
			(HapticPatternSelection) patternComboBox.getSelectedItem();
		if (pattern == null)
		{
			return;
		}

		if (selectedCategory == AlertCategory.GENERIC_NOTIFICATION)
		{
			genericIntensityPercent = intensitySlider.getValue();
			genericDurationMillis = ((Number) durationSpinner.getValue()).intValue();
			genericPattern = pattern;
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_INTENSITY_PERCENT_KEY,
				genericIntensityPercent
			);
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.NOTIFICATION_DURATION_MILLIS_KEY,
				genericDurationMillis
			);
			persistGenericPattern();
			return;
		}

		AlertBehavior behavior = (AlertBehavior) behaviorComboBox.getSelectedItem();
		if (behavior == null)
		{
			return;
		}
		alertProfiles = alertProfiles.withProfile(
			selectedCategory,
			new AlertProfile(
				behavior,
				intensitySlider.getValue(),
				((Number) durationSpinner.getValue()).intValue(),
				pattern,
				((Number) thresholdSpinner.getValue()).intValue()
			)
		);
		persistProfiles();
	}

	private void updateControlState()
	{
		boolean generic = selectedCategory == AlertCategory.GENERIC_NOTIFICATION;
		AlertBehavior behavior = generic
			? AlertBehavior.CUSTOM
			: (AlertBehavior) behaviorComboBox.getSelectedItem();
		boolean customConfiguration = behavior == AlertBehavior.CUSTOM;
		HapticPatternSelection pattern =
			(HapticPatternSelection) patternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();

		categoryComboBox.setEnabled(true);
		respectFocusCheckBox.setEnabled(true);
		behaviorComboBox.setEnabled(!generic);
		thresholdSpinner.setEnabled(selectedCategory.isThresholdBased());
		patternComboBox.setEnabled(customConfiguration);
		intensitySlider.setEnabled(customConfiguration && externallyScaled);
		intensityValueLabel.setEnabled(customConfiguration && externallyScaled);
		durationSpinner.setEnabled(customConfiguration && externallyScaled);
		testButton.setEnabled(connected && behavior != AlertBehavior.OFF);
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

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
