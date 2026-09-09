package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.AlertBehavior;
import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.AlertProfile;
import com.ashy0019.hapticscape.AlertProfiles;
import com.ashy0019.hapticscape.AlertTriggerParameter;
import com.ashy0019.hapticscape.AlertTriggerSettings;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.NotificationFeedbackSettings;
import com.ashy0019.hapticscape.clicker.ClickerAlertSettings;
import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

final class AlertsPanel extends JPanel
{
	private static final int WIDE_BREAKPOINT = 1120;
	private static final int MEDIUM_BREAKPOINT = 720;

	private final SettingsChangeSink settingsSink;
	private final Supplier<CustomPatternLibrary> customPatternsSupplier;
	private final RemoteSessionManager sessionManager;
	private final SettingsLockService lockService;
	private final SettingsLockDraft lockDraft;
	private final BooleanSupplier editingRemoteSubject;
	private final BooleanSupplier lockSelectionEnabled;
	private final JPanel layoutPanel = new JPanel(new GridBagLayout());
	private final JList<AlertCategory> categoryList =
		new JList<>(AlertCategory.values());
	private final JLabel selectedCategoryLabel = new JLabel();
	private final JLabel behaviorHintLabel = new JLabel();
	private final JPanel customProfilePanel = new JPanel();
	private JPanel specificPanel;
	private int layoutMode = -1;

	private final JCheckBox genericEnabledCheckBox =
		new JCheckBox("Catch-all haptics");
	private final JCheckBox genericClickEnabledCheckBox =
		new JCheckBox("Catch-all click");
	private final JCheckBox respectFocusCheckBox = new JCheckBox("Respect gameplay focus");
	private final JSlider genericIntensitySlider;
	private final JLabel genericIntensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> genericPatternComboBox;
	private final JSpinner genericDurationSpinner;
	private final JButton testGenericButton = new JButton("Test generic profile");

	private final JComboBox<AlertBehavior> behaviorComboBox =
		new JComboBox<>(AlertBehavior.values());
	private final JCheckBox specificClickEnabledCheckBox =
		new JCheckBox("Click sound");
	private final JPanel triggerRow = new JPanel(new BorderLayout(8, 0));
	private final JLabel triggerLabel = new JLabel();
	private final JSpinner triggerSpinner = new JSpinner();
	private final JSlider specificIntensitySlider;
	private final JLabel specificIntensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> specificPatternComboBox;
	private final JSpinner specificDurationSpinner;
	private final JButton testSpecificButton = new JButton("Test alert");
	private final LockableCheckBoxBinding genericEnabledLockBinding;
	private final LockableCheckBoxBinding genericClickLockBinding;
	private final LockableCheckBoxBinding respectFocusLockBinding;
	private final LockableCheckBoxBinding specificClickLockBinding;
	private final LockableSectionHeader genericBlockHeader;

	private volatile boolean genericEnabled;
	private volatile boolean genericClickEnabled;
	private volatile boolean respectFocus;
	private volatile int genericIntensityPercent;
	private volatile int genericDurationMillis;
	private volatile HapticPatternSelection genericPattern;
	private volatile AlertProfiles alertProfiles;
	private volatile ClickerAlertSettings clickerAlertSettings;
	private volatile AlertTriggerSettings triggerSettings;
	private volatile AlertCategory selectedCategory = AlertCategory.DIRECT_MESSAGE;
	private boolean updatingGenericControls;
	private boolean updatingSpecificControls;
	private boolean updatingPatternChoices;
	private boolean connected;
	private boolean remoteReadOnly;
	private boolean previewAllowed = true;

	AlertsPanel(
		HapticScapeSettingsSource config,
		SettingsChangeSink settingsSink,
		Supplier<CustomPatternLibrary> customPatternsSupplier,
		Runnable testGenericAction,
		Consumer<AlertCategory> testSpecificAction,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.settingsSink = settingsSink;
		this.customPatternsSupplier = customPatternsSupplier;
		this.sessionManager = sessionManager;
		this.lockService = lockService;
		this.lockDraft = lockDraft;
		this.editingRemoteSubject = editingRemoteSubject;
		this.lockSelectionEnabled = lockSelectionEnabled;
		genericEnabled = config.notificationFeedbackEnabled();
		genericClickEnabled = config.clickerGenericNotificationEnabled();
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
		clickerAlertSettings = ClickerAlertSettings.fromConfigValue(
			config.clickerAlertSettings()
		);
		triggerSettings = AlertTriggerSettings.fromConfigValues(
			config.alertTriggerSettings(),
			configuredProfiles
		);

		setName("alertsWorkspace");
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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
		genericBlockHeader = new LockableSectionHeader(
			"",
			() -> SettingsLockCatalog.GENERIC_ALERTS_BLOCK,
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled
		);
		categoryList.setName("alertCategoryList");
		categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		categoryList.setVisibleRowCount(AlertCategory.values().length);
		categoryList.setFixedCellHeight(43);
		categoryList.setCellRenderer(new AlertCategoryRenderer());
		categoryList.setSelectedValue(selectedCategory, true);
		JPanel categoriesPanel = createCategoriesPanel();
		specificPanel = createSpecificPanel();
		JPanel genericPanel = createGenericPanel();
		JPanel categoriesHost = host(categoriesPanel, 300);
		JPanel specificHost = host(specificPanel, 390);
		JPanel genericHost = host(genericPanel, 360);
		add(layoutPanel, BorderLayout.NORTH);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflow(categoriesHost, specificHost, genericHost);
			}
		});
		reflow(categoriesHost, specificHost, genericHost);
		genericEnabledLockBinding = binding(
			genericEnabledCheckBox,
			SettingsLockCatalog.GENERIC_NOTIFICATION_HAPTICS,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> genericEnabled
		);
		genericClickLockBinding = binding(
			genericClickEnabledCheckBox,
			SettingsLockCatalog.GENERIC_NOTIFICATION_CLICKS,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> genericClickEnabled
		);
		respectFocusLockBinding = binding(
			respectFocusCheckBox,
			SettingsLockCatalog.NOTIFICATION_RESPECT_FOCUS,
			lockDraft,
			lockService,
			sessionManager,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> respectFocus
		);
		specificClickLockBinding = new LockableCheckBoxBinding(
			specificClickEnabledCheckBox,
			() -> SettingsLockCatalog.alertClicks(selectedCategory),
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> clickerAlertSettings.isEnabled(selectedCategory)
		);
		configureListeners(testGenericAction, testSpecificAction);
		lockDraft.addListener(this::refreshAlertLockState);

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

	boolean isGenericClickEnabled()
	{
		return genericClickEnabled;
	}

	boolean isClickEnabled(AlertCategory category)
	{
		return clickerAlertSettings.isEnabled(category);
	}

	AlertTriggerSettings getTriggerSettings()
	{
		return triggerSettings;
	}

	AlertCategory getSelectedCategory()
	{
		return selectedCategory;
	}

	void applyDisplayedSettings(
		NotificationFeedbackSettings genericSettings,
		boolean displayedGenericClickEnabled,
		AlertProfiles displayedProfiles,
		AlertTriggerSettings displayedTriggers,
		ClickerAlertSettings displayedClickSettings,
		CustomPatternLibrary library)
	{
		genericEnabled = genericSettings.isEnabled();
		genericClickEnabled = displayedGenericClickEnabled;
		respectFocus = genericSettings.isRespectSourceFocus();
		genericIntensityPercent = genericSettings.getIntensityPercent();
		genericDurationMillis = genericSettings.getDurationMillis();
		genericPattern = genericSettings.getPatternSelection().resolveAgainst(library);
		alertProfiles = displayedProfiles.replaceMissingCustomPatterns(library);
		triggerSettings = displayedTriggers;
		clickerAlertSettings = displayedClickSettings;

		updatingGenericControls = true;
		updatingPatternChoices = true;
		try
		{
			genericEnabledCheckBox.setSelected(genericEnabled);
			genericClickEnabledCheckBox.setSelected(genericClickEnabled);
			respectFocusCheckBox.setSelected(respectFocus);
			genericIntensitySlider.setValue(genericIntensityPercent);
			genericIntensityValueLabel.setText(genericIntensityPercent + "%");
			PanelUi.setPatternChoices(genericPatternComboBox, genericPattern, library);
			genericDurationSpinner.setValue(genericDurationMillis);
			PanelUi.setPatternChoices(
				specificPatternComboBox,
				alertProfiles.get(selectedCategory).getPatternSelection(),
				library
			);
		}
		finally
		{
			updatingPatternChoices = false;
			updatingGenericControls = false;
		}
		loadSelectedCategory();
		updateGenericControlState();
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		updateGenericControlState();
		updateSpecificControlState();
	}

	void setPreviewAllowed(boolean previewAllowed)
	{
		this.previewAllowed = previewAllowed;
		updateGenericControlState();
		updateSpecificControlState();
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

	private JPanel createCategoriesPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 5));
		panel.setBorder(PanelUi.createSectionBorder("Alert types"));
		JLabel help = new JLabel("Select an event to edit");
		help.setEnabled(false);
		panel.add(help, BorderLayout.NORTH);
		JScrollPane scrollPane = new JScrollPane(categoryList);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setToolTipText(
			"Shift-click an alert during Remote Play to select its post-session lock."
		);
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private JPanel createGenericPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(PanelUi.createSectionBorder("Generic defaults"));
		PanelUi.addPreferredHeightComponent(panel, genericBlockHeader);

		genericEnabledCheckBox.setSelected(genericEnabled);
		genericEnabledCheckBox.setToolTipText(
			"Play the Generic profile for unclassified source notifications"
		);
		genericClickEnabledCheckBox.setSelected(genericClickEnabled);
		genericClickEnabledCheckBox.setToolTipText(
			"Play one click for an unclassified source notification"
		);
		respectFocusCheckBox.setSelected(respectFocus);
		respectFocusCheckBox.setToolTipText(
			"Honor the event source focus policy for generic notifications"
		);
		genericIntensityValueLabel.setText(genericIntensityPercent + "%");

		PanelUi.addPreferredHeightComponent(panel, genericEnabledCheckBox);
		PanelUi.addPreferredHeightComponent(panel, genericClickEnabledCheckBox);
		PanelUi.addPreferredHeightComponent(panel, respectFocusCheckBox);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(genericIntensityValueLabel, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(panel, intensityHeader);
		PanelUi.addPreferredHeightComponent(panel, genericIntensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(genericPatternComboBox, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(panel, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(genericDurationSpinner, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(panel, durationRow);

		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testGenericButton, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(panel, testRow);
		return panel;
	}

	private JPanel createSpecificPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(PanelUi.createSectionBorder("Selected alert"));
		selectedCategoryLabel.setFont(
			selectedCategoryLabel.getFont().deriveFont(Font.BOLD)
		);
		selectedCategoryLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 5, 2));
		PanelUi.addPreferredHeightComponent(panel, selectedCategoryLabel);
		specificClickEnabledCheckBox.setToolTipText(
			"Play one click for the selected semantic alert"
		);
		PanelUi.addPreferredHeightComponent(panel, specificClickEnabledCheckBox);

		JPanel behaviorRow = new JPanel(new BorderLayout(8, 0));
		behaviorRow.add(new JLabel("Haptics"), BorderLayout.CENTER);
		behaviorRow.add(behaviorComboBox, BorderLayout.EAST);
		PanelUi.setFixedWidth(behaviorComboBox, PanelUi.SELECTOR_CONTROL_WIDTH);
		PanelUi.addPreferredHeightComponent(panel, behaviorRow);
		behaviorHintLabel.setEnabled(false);
		behaviorHintLabel.setBorder(BorderFactory.createEmptyBorder(3, 2, 4, 2));
		PanelUi.addPreferredHeightComponent(panel, behaviorHintLabel);

		triggerRow.add(triggerLabel, BorderLayout.CENTER);
		triggerRow.add(triggerSpinner, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(panel, triggerRow);

		customProfilePanel.setLayout(
			new BoxLayout(customProfilePanel, BoxLayout.Y_AXIS)
		);
		customProfilePanel.setBorder(PanelUi.createSectionBorder("Custom haptics"));
		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(specificIntensityValueLabel, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(customProfilePanel, intensityHeader);
		PanelUi.addPreferredHeightComponent(customProfilePanel, specificIntensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(specificPatternComboBox, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(customProfilePanel, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(specificDurationSpinner, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(customProfilePanel, durationRow);
		PanelUi.addFlexibleVerticalComponent(panel, customProfilePanel);

		JPanel testRow = new JPanel(new BorderLayout());
		testRow.add(testSpecificButton, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(panel, testRow);
		return panel;
	}

	private void configureListeners(
		Runnable testGenericAction,
		Consumer<AlertCategory> testSpecificAction)
	{
		genericEnabledCheckBox.addActionListener(event ->
		{
			if (updatingGenericControls || genericEnabledLockBinding.handleAction(event))
			{
				return;
			}
			if (isGenericReadOnly() || genericEnabledLockBinding.isEditLocked())
			{
				return;
			}
			genericEnabled = genericEnabledCheckBox.isSelected();
			settingsSink.set(
				SettingsLockCatalog.GENERIC_NOTIFICATION_HAPTICS,
				HapticScapeSettingKeys.NOTIFICATION_FEEDBACK_ENABLED,
				genericEnabled
			);
			updateGenericControlState();
		});
		genericClickEnabledCheckBox.addActionListener(event ->
		{
			if (updatingGenericControls || genericClickLockBinding.handleAction(event))
			{
				return;
			}
			if (isGenericReadOnly() || genericClickLockBinding.isEditLocked())
			{
				return;
			}
			genericClickEnabled = genericClickEnabledCheckBox.isSelected();
			settingsSink.set(
				SettingsLockCatalog.GENERIC_NOTIFICATION_CLICKS,
				HapticScapeSettingKeys.CLICKER_GENERIC_NOTIFICATION_ENABLED,
				genericClickEnabled
			);
			updateGenericControlState();
		});
		respectFocusCheckBox.addActionListener(event ->
		{
			if (updatingGenericControls || respectFocusLockBinding.handleAction(event))
			{
				return;
			}
			if (isGenericReadOnly() || respectFocusLockBinding.isEditLocked())
			{
				return;
			}
			respectFocus = respectFocusCheckBox.isSelected();
			settingsSink.set(
				SettingsLockCatalog.NOTIFICATION_RESPECT_FOCUS,
				HapticScapeSettingKeys.NOTIFICATION_RESPECT_FOCUS,
				respectFocus
			);
		});
		genericIntensitySlider.addChangeListener(event ->
		{
			genericIntensityValueLabel.setText(genericIntensitySlider.getValue() + "%");
			if (!genericIntensitySlider.getValueIsAdjusting()
				&& !updatingGenericControls
				&& !isGenericReadOnly())
			{
				genericIntensityPercent = genericIntensitySlider.getValue();
				settingsSink.set(
					SettingsLockCatalog.GENERIC_ALERTS_BLOCK,
					HapticScapeSettingKeys.NOTIFICATION_INTENSITY_PERCENT,
					genericIntensityPercent
				);
			}
		});
		genericPatternComboBox.addActionListener(event ->
		{
			if (updatingGenericControls || updatingPatternChoices || isGenericReadOnly())
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
			if (!updatingGenericControls && !isGenericReadOnly())
			{
				genericDurationMillis = ((Number) genericDurationSpinner.getValue()).intValue();
				settingsSink.set(
					SettingsLockCatalog.GENERIC_ALERTS_BLOCK,
					HapticScapeSettingKeys.NOTIFICATION_DURATION_MILLIS,
					genericDurationMillis
				);
			}
		});
		testGenericButton.addActionListener(event -> testGenericAction.run());

		categoryList.addListSelectionListener(event ->
		{
			AlertCategory category = categoryList.getSelectedValue();
			if (!event.getValueIsAdjusting() && category != null)
			{
				selectedCategory = category;
				loadSelectedCategory();
			}
		});
		categoryList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				handleCategoryShiftClick(event);
			}
		});
		specificClickEnabledCheckBox.addActionListener(event ->
		{
			if (updatingSpecificControls || specificClickLockBinding.handleAction(event))
			{
				return;
			}
			if (isSpecificReadOnly() || specificClickLockBinding.isEditLocked())
			{
				return;
			}
			clickerAlertSettings = clickerAlertSettings.withEnabled(
				selectedCategory,
				specificClickEnabledCheckBox.isSelected()
			);
			persistClickerAlertSettings(SettingsLockCatalog.alertClicks(selectedCategory));
			updateSpecificControlState();
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
		selectedCategoryLabel.setText(selectedCategory.getDisplayName());
		selectedCategoryLabel.setToolTipText(
			"Settings for " + selectedCategory.getDisplayName().toLowerCase()
		);
		updatingSpecificControls = true;
		try
		{
			specificClickEnabledCheckBox.setSelected(
				clickerAlertSettings.isEnabled(selectedCategory)
			);
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
		categoryList.repaint();
		revalidate();
		repaint();
	}

	private void updateSelectedProfile()
	{
		if (isSpecificReadOnly() || updatingSpecificControls || updatingPatternChoices)
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
		persistProfiles(SettingsLockCatalog.alertBlock(selectedCategory));
	}

	private void updateSelectedTrigger()
	{
		if (isSpecificReadOnly()
			|| updatingSpecificControls
			|| !selectedCategory.hasTriggerParameter())
		{
			return;
		}
		triggerSettings = triggerSettings.withValue(
			selectedCategory,
			((Number) triggerSpinner.getValue()).intValue()
		);
		persistTriggerSettings(SettingsLockCatalog.alertBlock(selectedCategory));
		categoryList.repaint();
	}

	private void updateGenericControlState()
	{
		boolean editable = !remoteReadOnly;
		genericBlockHeader.refresh();
		boolean blockEditable = editable && !genericBlockHeader.isEditLocked();
		HapticPatternSelection pattern =
			(HapticPatternSelection) genericPatternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();
		genericEnabledLockBinding.refresh();
		genericClickLockBinding.refresh();
		respectFocusLockBinding.refresh();
		genericEnabledCheckBox.setEnabled(blockEditable && !genericEnabledLockBinding.isEditLocked());
		genericClickEnabledCheckBox.setEnabled(blockEditable && !genericClickLockBinding.isEditLocked());
		respectFocusCheckBox.setEnabled(blockEditable && !respectFocusLockBinding.isEditLocked());
		genericPatternComboBox.setEnabled(blockEditable);
		genericIntensitySlider.setEnabled(blockEditable && externallyScaled);
		genericIntensityValueLabel.setEnabled(blockEditable && externallyScaled);
		genericDurationSpinner.setEnabled(blockEditable && externallyScaled);
		testGenericButton.setEnabled(
			previewAllowed && editable && (connected || genericClickEnabled)
		);
	}

	private void updateSpecificControlState()
	{
		boolean editable = !remoteReadOnly;
		boolean blockEditable = editable && !isSelectedAlertPersistentlyLocked();
		AlertBehavior behavior = (AlertBehavior) behaviorComboBox.getSelectedItem();
		boolean customConfiguration = behavior == AlertBehavior.CUSTOM;
		HapticPatternSelection pattern =
			(HapticPatternSelection) specificPatternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();

		// Category selection is navigation only, so the participant can inspect every alert.
		categoryList.setEnabled(true);
		behaviorComboBox.setEnabled(blockEditable);
		specificClickLockBinding.refresh();
		specificClickEnabledCheckBox.setEnabled(
			blockEditable && !specificClickLockBinding.isEditLocked()
		);
		triggerSpinner.setEnabled(blockEditable && selectedCategory.hasTriggerParameter());
		specificPatternComboBox.setEnabled(blockEditable && customConfiguration);
		specificIntensitySlider.setEnabled(blockEditable && customConfiguration && externallyScaled);
		specificIntensityValueLabel.setEnabled(blockEditable && customConfiguration && externallyScaled);
		specificDurationSpinner.setEnabled(blockEditable && customConfiguration && externallyScaled);
		testSpecificButton.setEnabled(
			previewAllowed && editable && ((connected && behavior != AlertBehavior.OFF)
				|| clickerAlertSettings.isEnabled(selectedCategory))
		);
		updateBehaviorPresentation(behavior);
		categoryList.repaint();
	}

	private void updateBehaviorPresentation(AlertBehavior behavior)
	{
		boolean custom = behavior == AlertBehavior.CUSTOM;
		customProfilePanel.setVisible(custom);
		if (behavior == AlertBehavior.USE_GENERIC)
		{
			behaviorHintLabel.setText("Uses the settings in Generic defaults.");
		}
		else if (behavior == AlertBehavior.OFF)
		{
			behaviorHintLabel.setText("Haptics are off; the click sound can remain enabled.");
		}
		else
		{
			behaviorHintLabel.setText("Uses the custom haptic profile below.");
		}
		if (specificPanel != null)
		{
			specificPanel.revalidate();
			specificPanel.repaint();
		}
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
		settingsSink.set(
			SettingsLockCatalog.GENERIC_ALERTS_BLOCK,
			HapticScapeSettingKeys.NOTIFICATION_PATTERN_PRESET,
			genericPattern.toConfigValue()
		);
	}

	private void persistProfiles()
	{
		settingsSink.set(
			HapticScapeSettingKeys.ALERT_PROFILES,
			alertProfiles.toConfigValue()
		);
	}

	private void persistProfiles(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeSettingKeys.ALERT_PROFILES,
			alertProfiles.toConfigValue()
		);
	}

	private void persistTriggerSettings()
	{
		settingsSink.set(
			HapticScapeSettingKeys.ALERT_TRIGGER_SETTINGS,
			triggerSettings.toConfigValue()
		);
	}

	private void persistTriggerSettings(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeSettingKeys.ALERT_TRIGGER_SETTINGS,
			triggerSettings.toConfigValue()
		);
	}

	private boolean isGenericReadOnly()
	{
		return remoteReadOnly || genericBlockHeader.isEditLocked();
	}

	private boolean isSpecificReadOnly()
	{
		return remoteReadOnly || isSelectedAlertPersistentlyLocked();
	}

	private boolean isSelectedAlertPersistentlyLocked()
	{
		return !editingRemoteSubject.getAsBoolean()
			&& lockService.isLocked(SettingsLockCatalog.alertBlock(selectedCategory));
	}

	private void handleCategoryShiftClick(MouseEvent event)
	{
		if ((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0
			|| !editingRemoteSubject.getAsBoolean()
			|| !lockSelectionEnabled.getAsBoolean())
		{
			return;
		}
		int index = categoryList.locationToIndex(event.getPoint());
		if (index < 0)
		{
			return;
		}
		Rectangle bounds = categoryList.getCellBounds(index, index);
		if (bounds == null || !bounds.contains(event.getPoint()))
		{
			return;
		}
		AlertCategory category = categoryList.getModel().getElementAt(index);
		categoryList.setSelectedIndex(index);
		lockDraft.toggle(SettingsLockCatalog.alertBlock(category));
		event.consume();
	}

	private void refreshAlertLockState()
	{
		categoryList.repaint();
		updateSpecificControlState();
	}

	private AlertLockState alertLockState(AlertCategory category)
	{
		SettingsLockTarget target = SettingsLockCatalog.alertBlock(category);
		if (editingRemoteSubject.getAsBoolean())
		{
			RemoteLockSnapshot remote = sessionManager.getLockSnapshot();
			if (remote.getTargets().contains(target)
				&& remote.getState() == RemoteLockState.ARMED)
			{
				return AlertLockState.ARMED;
			}
			if (remote.getTargets().contains(target) || lockDraft.contains(target))
			{
				return AlertLockState.PROPOSED;
			}
			return AlertLockState.NONE;
		}
		return lockService.isLocked(target)
			? AlertLockState.PERSISTENT
			: AlertLockState.NONE;
	}

	private void persistClickerAlertSettings(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeSettingKeys.CLICKER_ALERT_SETTINGS,
			clickerAlertSettings.toConfigValue()
		);
	}

	private void reflow(
		Component categories,
		Component specific,
		Component generic)
	{
		int desired = layoutModeForWidth(getWidth());
		if (desired == layoutMode)
		{
			return;
		}
		layoutMode = desired;
		layoutPanel.removeAll();
		if (layoutMode == 3)
		{
			addSection(categories, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(specific, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(generic, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.HORIZONTAL);
			addRemainder(3, 0);
		}
		else if (layoutMode == 2)
		{
			addSection(categories, 0, 0, 1, 2, 0.44, 1.0, GridBagConstraints.BOTH);
			addSection(specific, 1, 0, 1, 1, 0.56, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(generic, 1, 1, 1, 1, 0.56, 1.0, GridBagConstraints.HORIZONTAL);
		}
		else
		{
			addSection(categories, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(specific, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
			addSection(generic, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
		}
		layoutPanel.revalidate();
		layoutPanel.repaint();
	}

	static int layoutModeForWidth(int width)
	{
		return width >= WIDE_BREAKPOINT ? 3 : width >= MEDIUM_BREAKPOINT ? 2 : 1;
	}

	private void addSection(
		Component component,
		int x,
		int y,
		int width,
		int height,
		double weightX,
		double weightY,
		int fill)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.gridwidth = width;
		constraints.gridheight = height;
		constraints.weightx = weightX;
		constraints.weighty = weightY;
		constraints.fill = fill;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = new Insets(0, 0, 7, 7);
		layoutPanel.add(component, constraints);
	}

	private void addRemainder(int x, int y)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		layoutPanel.add(new JPanel(), constraints);
	}

	private String categorySummary(AlertCategory category)
	{
		AlertBehavior behavior = alertProfiles.get(category).getBehavior();
		String haptics;
		switch (behavior)
		{
			case USE_GENERIC:
				haptics = "Generic haptics";
				break;
			case CUSTOM:
				haptics = "Custom haptics";
				break;
			case OFF:
			default:
				haptics = "Haptics off";
				break;
		}
		StringBuilder summary = new StringBuilder(haptics)
			.append(clickerAlertSettings.isEnabled(category) ? " · Click on" : " · Click off");
		if (category.hasTriggerParameter())
		{
			summary.append(" · ")
				.append(category.getTriggerParameter().getLabel())
				.append(' ')
				.append(triggerSettings.get(category));
		}
		return summary.toString();
	}

	private static JPanel host(Component component, int preferredWidth)
	{
		JPanel host = new WidthHintPanel(preferredWidth);
		host.add(component, BorderLayout.NORTH);
		return host;
	}

	private final class AlertCategoryRenderer extends JPanel
		implements ListCellRenderer<AlertCategory>
	{
		private final JLabel title = new JLabel();
		private final JLabel summary = new JLabel();
		private final JLabel lockMarker = new JLabel();

		private AlertCategoryRenderer()
		{
			super(new BorderLayout(0, 1));
			setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
			title.setFont(title.getFont().deriveFont(Font.BOLD));
			summary.setFont(summary.getFont().deriveFont(10f));
			add(title, BorderLayout.NORTH);
			add(summary, BorderLayout.SOUTH);
			add(lockMarker, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends AlertCategory> list,
			AlertCategory value,
			int index,
			boolean selected,
			boolean focused)
		{
			title.setText(value.getDisplayName());
			summary.setText(categorySummary(value));
			lockMarker.setIcon(new AlertLockIcon(value));
			Color background = selected
				? list.getSelectionBackground()
				: list.getBackground();
			Color foreground = selected
				? list.getSelectionForeground()
				: list.getForeground();
			setBackground(background);
			title.setForeground(foreground);
			summary.setForeground(foreground);
			setOpaque(true);
			return this;
		}
	}

	private enum AlertLockState
	{
		NONE,
		PROPOSED,
		ARMED,
		PERSISTENT
	}

	private final class AlertLockIcon implements Icon
	{
		private final AlertCategory category;

		private AlertLockIcon(AlertCategory category)
		{
			this.category = category;
		}

		@Override
		public int getIconWidth()
		{
			return 11;
		}

		@Override
		public int getIconHeight()
		{
			return 11;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			AlertLockState state = alertLockState(category);
			if (state == AlertLockState.NONE)
			{
				return;
			}
			graphics.setColor(state == AlertLockState.PERSISTENT
				? new Color(170, 170, 170)
				: new Color(255, 174, 0));
			graphics.drawArc(x + 2, y, 5, 6, 0, 180);
			if (state == AlertLockState.ARMED || state == AlertLockState.PERSISTENT)
			{
				graphics.fillRect(x + 1, y + 5, 8, 6);
			}
			else
			{
				graphics.drawRect(x + 1, y + 5, 7, 5);
			}
		}
	}

	/** Width hint keeps wide layouts orderly while allowing dynamic editor height. */
	private static final class WidthHintPanel extends JPanel
	{
		private final int preferredWidth;

		private WidthHintPanel(int preferredWidth)
		{
			super(new BorderLayout());
			this.preferredWidth = preferredWidth;
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension preferred = super.getPreferredSize();
			return new Dimension(Math.max(preferredWidth, preferred.width), preferred.height);
		}
	}

	private static LockableCheckBoxBinding binding(
		JCheckBox checkBox,
		SettingsLockTarget target,
		SettingsLockDraft lockDraft,
		SettingsLockService lockService,
		RemoteSessionManager sessionManager,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled,
		BooleanSupplier authoritativeValue)
	{
		return new LockableCheckBoxBinding(
			checkBox,
			target,
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled,
			authoritativeValue
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
