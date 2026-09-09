package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.SkillDescriptor;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
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

final class ProfilesPanel extends JPanel
{
	private final SettingsChangeSink settingsSink;
	private final Supplier<XpFeedbackSettings> globalSettingsSupplier;
	private final Supplier<CustomPatternLibrary> customPatternsSupplier;
	private final Map<String, SkillDescriptor> skillsById = new LinkedHashMap<>();
	private final JLabel selectedSkillLabel = new JLabel();
	private final JCheckBox useGlobalCheckBox = new JCheckBox("Use global XP settings");
	private final JSpinner minimumXpSpinner;
	private final JSlider intensitySlider;
	private final JLabel intensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> patternComboBox;
	private final JSpinner durationSpinner;
	private final JButton testButton = new JButton("Test selected skill");
	private final LockableCheckBoxBinding useGlobalLockBinding;
	private final LockableSectionHeader profileBlockHeader;

	private volatile SkillFeedbackProfiles profiles;
	private volatile SkillDescriptor selectedSkill;
	private boolean updatingControls;
	private boolean updatingPatternChoices;
	private boolean connected;
	private boolean remoteReadOnly;
	private boolean previewAllowed = true;

	ProfilesPanel(
		SkillCatalog skillCatalog,
		SkillFeedbackProfiles profiles,
		SettingsChangeSink settingsSink,
		Supplier<XpFeedbackSettings> globalSettingsSupplier,
		Supplier<CustomPatternLibrary> customPatternsSupplier,
		Runnable testAction,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.profiles = profiles;
		this.settingsSink = settingsSink;
		this.globalSettingsSupplier = globalSettingsSupplier;
		this.customPatternsSupplier = customPatternsSupplier;
		setName("skillProfileEditor");
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(PanelUi.createSectionBorder("Skill override"));

		SkillDescriptor[] skills = skillCatalog.getSkills().toArray(new SkillDescriptor[0]);
		for (SkillDescriptor skill : skills)
		{
			skillsById.put(skill.getId(), skill);
		}
		selectedSkill = skills[0];
		selectedSkillLabel.setFont(selectedSkillLabel.getFont().deriveFont(Font.BOLD));
		selectedSkillLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 5, 2));
		profileBlockHeader = new LockableSectionHeader(
			"XP feedback",
			() -> SettingsLockCatalog.profileBlock(selectedSkill.getId()),
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled
		);

		XpFeedbackSettings global = globalSettingsSupplier.get();
		minimumXpSpinner = new JSpinner(new SpinnerNumberModel(
			global.getMinimumXpGain(),
			XpFeedbackSettings.MINIMUM_XP_GAIN,
			XpFeedbackSettings.MAXIMUM_XP_GAIN,
			1
		));
		PanelUi.setFixedWidth(minimumXpSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		intensitySlider = new JSlider(
			XpFeedbackSettings.MINIMUM_INTENSITY_PERCENT,
			XpFeedbackSettings.MAXIMUM_INTENSITY_PERCENT,
			global.getIntensityPercent()
		);
		patternComboBox = PanelUi.createPatternComboBox(customPatternsSupplier);
		durationSpinner = new JSpinner(new SpinnerNumberModel(
			global.getDurationMillis(),
			XpFeedbackSettings.MINIMUM_DURATION_MILLIS,
			XpFeedbackSettings.MAXIMUM_DURATION_MILLIS,
			50
		));
		PanelUi.setFixedWidth(durationSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);
		intensityValueLabel.setText(global.getIntensityPercent() + "%");

		PanelUi.addPreferredHeightComponent(this, selectedSkillLabel);
		PanelUi.addPreferredHeightComponent(this, profileBlockHeader);
		PanelUi.addPreferredHeightComponent(this, useGlobalCheckBox);
		add(Box.createVerticalStrut(6));

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum XP gain"), BorderLayout.CENTER);
		thresholdRow.add(minimumXpSpinner, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(this, thresholdRow);

		JPanel intensityHeader = new JPanel(new BorderLayout());
		intensityHeader.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityHeader.add(intensityValueLabel, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(this, intensityHeader);
		PanelUi.addPreferredHeightComponent(this, intensitySlider);

		JPanel patternRow = new JPanel(new BorderLayout(8, 0));
		patternRow.add(new JLabel("Pattern"), BorderLayout.CENTER);
		patternRow.add(patternComboBox, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(this, patternRow);

		JPanel durationRow = new JPanel(new BorderLayout(8, 0));
		durationRow.add(new JLabel(PanelUi.DURATION_LABEL), BorderLayout.CENTER);
		durationRow.add(durationSpinner, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(this, durationRow);

		JPanel testRow = new JPanel(new BorderLayout());
		testButton.setToolTipText("Preview the selected skill's effective XP settings");
		testRow.add(testButton, BorderLayout.EAST);
		PanelUi.addPreferredHeightComponent(this, testRow);

		useGlobalLockBinding = new LockableCheckBoxBinding(
			useGlobalCheckBox,
			() -> SettingsLockCatalog.profileUsesGlobal(selectedSkill.getId()),
			lockDraft,
			lockService,
			sessionManager::getLockSnapshot,
			editingRemoteSubject,
			lockSelectionEnabled,
			() -> !profiles.getOverride(selectedSkill.getId()).isPresent()
		);
		useGlobalCheckBox.addActionListener(event ->
		{
			if (!useGlobalLockBinding.handleAction(event))
			{
				toggleOverride();
			}
		});
		minimumXpSpinner.addChangeListener(event -> updateSelectedProfile());
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
		testButton.addActionListener(event -> testAction.run());
		loadSelectedProfile();
		setConnected(false);
	}

	void selectSkill(SkillDescriptor skill)
	{
		if (skill == null)
		{
			return;
		}
		SkillDescriptor known = skillsById.get(skill.getId());
		if (known == null)
		{
			return;
		}
		selectedSkill = known;
		profileBlockHeader.refresh();
		loadSelectedProfile();
	}

	XpFeedbackSettings getSettings(String skillId)
	{
		return profiles.resolve(skillId, globalSettingsSupplier.get());
	}

	String getSelectedSkillId()
	{
		return selectedSkill == null ? null : selectedSkill.getId();
	}

	void applyDisplayedSettings(
		SkillFeedbackProfiles displayedProfiles,
		CustomPatternLibrary library)
	{
		profiles = displayedProfiles.replaceMissingCustomPatterns(library);
		updatingPatternChoices = true;
		try
		{
			PanelUi.setPatternChoices(
				patternComboBox,
				getSettings(selectedSkill.getId()).getPatternSelection(),
				library
			);
		}
		finally
		{
			updatingPatternChoices = false;
		}
		loadSelectedProfile();
	}

	void setRemoteReadOnly(boolean remoteReadOnly)
	{
		this.remoteReadOnly = remoteReadOnly;
		updateControlState();
	}

	void setPreviewAllowed(boolean previewAllowed)
	{
		this.previewAllowed = previewAllowed;
		updateControlState();
	}

	void refreshInheritedProfile()
	{
		if (selectedSkill != null && !profiles.getOverride(selectedSkill.getId()).isPresent())
		{
			loadSelectedProfile();
		}
	}

	void applyCustomPatternLibrary(CustomPatternLibrary library)
	{
		SkillFeedbackProfiles resolved = profiles.replaceMissingCustomPatterns(library);
		if (resolved != profiles)
		{
			profiles = resolved;
			persist();
		}
		updatingPatternChoices = true;
		try
		{
			PanelUi.setPatternChoices(
				patternComboBox,
				getSettings(selectedSkill.getId()).getPatternSelection(),
				library
			);
		}
		finally
		{
			updatingPatternChoices = false;
		}
		loadSelectedProfile();
	}

	void setConnected(boolean connected)
	{
		this.connected = connected;
		testButton.setEnabled(connected);
	}

	private void toggleOverride()
	{
		if (remoteReadOnly
			|| updatingControls
			|| selectedSkill == null
			|| profileBlockHeader.isEditLocked()
			|| useGlobalLockBinding.isEditLocked())
		{
			return;
		}
		if (useGlobalCheckBox.isSelected())
		{
			profiles = profiles.withoutOverride(selectedSkill.getId());
		}
		else
		{
			profiles = profiles.withOverride(selectedSkill.getId(), globalSettingsSupplier.get());
		}
		persist(SettingsLockCatalog.profileUsesGlobal(selectedSkill.getId()));
		loadSelectedProfile();
	}

	private void loadSelectedProfile()
	{
		if (selectedSkill == null)
		{
			return;
		}
		XpFeedbackSettings override = profiles.getOverride(selectedSkill.getId()).orElse(null);
		selectedSkillLabel.setText(selectedSkill.getDisplayName());
		selectedSkillLabel.setToolTipText(
			"XP feedback settings for " + selectedSkill.getDisplayName()
		);
		XpFeedbackSettings displayed = override == null
			? globalSettingsSupplier.get()
			: override;

		updatingControls = true;
		try
		{
			useGlobalCheckBox.setSelected(override == null);
			minimumXpSpinner.setValue(displayed.getMinimumXpGain());
			intensitySlider.setValue(displayed.getIntensityPercent());
			intensityValueLabel.setText(displayed.getIntensityPercent() + "%");
			patternComboBox.setSelectedItem(displayed.getPatternSelection());
			durationSpinner.setValue(displayed.getDurationMillis());
		}
		finally
		{
			updatingControls = false;
		}
		updateControlState();
	}

	private void updateSelectedProfile()
	{
		if (remoteReadOnly
			|| updatingControls
			|| updatingPatternChoices
			|| selectedSkill == null
			|| profileBlockHeader.isEditLocked()
			|| useGlobalCheckBox.isSelected())
		{
			return;
		}
		HapticPatternSelection pattern =
			(HapticPatternSelection) patternComboBox.getSelectedItem();
		if (pattern == null)
		{
			return;
		}
		profiles = profiles.withOverride(
			selectedSkill.getId(),
			new XpFeedbackSettings(
				((Number) minimumXpSpinner.getValue()).intValue(),
				intensitySlider.getValue(),
				((Number) durationSpinner.getValue()).intValue(),
				pattern
			)
		);
		persist(SettingsLockCatalog.profileBlock(selectedSkill.getId()));
	}

	private void updateControlState()
	{
		boolean editable = !remoteReadOnly;
		profileBlockHeader.refresh();
		boolean blockEditable = editable && !profileBlockHeader.isEditLocked();
		boolean overridden = !useGlobalCheckBox.isSelected();
		HapticPatternSelection pattern =
			(HapticPatternSelection) patternComboBox.getSelectedItem();
		boolean externallyScaled = pattern == null || !pattern.isCustom();
		useGlobalLockBinding.refresh();
		useGlobalCheckBox.setEnabled(blockEditable && !useGlobalLockBinding.isEditLocked());
		minimumXpSpinner.setEnabled(blockEditable && overridden);
		patternComboBox.setEnabled(blockEditable && overridden);
		intensitySlider.setEnabled(blockEditable && overridden && externallyScaled);
		intensityValueLabel.setEnabled(blockEditable && overridden && externallyScaled);
		durationSpinner.setEnabled(blockEditable && overridden && externallyScaled);
		testButton.setEnabled(editable && connected && previewAllowed);
	}

	private void persist()
	{
		settingsSink.set(
			HapticScapeSettingKeys.SKILL_FEEDBACK_PROFILES,
			profiles.toConfigValue()
		);
	}

	private void persist(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeSettingKeys.SKILL_FEEDBACK_PROFILES,
			profiles.toConfigValue()
		);
	}
}
