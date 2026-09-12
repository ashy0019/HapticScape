package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.SkillClickProfiles;
import com.ashy0019.hapticscape.SkillDescriptor;
import com.ashy0019.hapticscape.SkillFeedbackProfiles;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.clicker.ClickSequence;
import com.ashy0019.hapticscape.clicker.ClickerXpSettings;
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
	private final Supplier<ClickerXpSettings> globalClickSettingsSupplier;
	private final Supplier<CustomPatternLibrary> customPatternsSupplier;
	private final Map<String, SkillDescriptor> skillsById = new LinkedHashMap<>();
	private final JLabel selectedSkillLabel = new JLabel();
	private final JCheckBox useGlobalCheckBox = new JCheckBox("Use global XP settings");
	private final JSpinner minimumXpSpinner;
	private final JSlider intensitySlider;
	private final JLabel intensityValueLabel = new JLabel();
	private final JComboBox<HapticPatternSelection> patternComboBox;
	private final JSpinner durationSpinner;
	private final JSpinner minimumClickXpSpinner;
	private final JComboBox<ClickSequence> xpClickSequenceComboBox =
		ClickSequenceControls.enabledOnly();
	private final JComboBox<ClickSequence> levelUpClickSequenceComboBox =
		ClickSequenceControls.override();
	private final JComboBox<ClickSequence> milestoneClickSequenceComboBox =
		ClickSequenceControls.override();
	private final JButton testButton = new JButton("Test selected skill");
	private final LockableCheckBoxBinding useGlobalLockBinding;
	private final LockableSectionHeader profileBlockHeader;

	private volatile SkillFeedbackProfiles profiles;
	private volatile SkillClickProfiles clickProfiles;
	private volatile SkillDescriptor selectedSkill;
	private boolean updatingControls;
	private boolean updatingPatternChoices;
	private boolean connected;
	private boolean remoteReadOnly;
	private boolean previewAllowed = true;

	ProfilesPanel(
		SkillCatalog skillCatalog,
		SkillFeedbackProfiles profiles,
		SkillClickProfiles clickProfiles,
		SettingsChangeSink settingsSink,
		Supplier<XpFeedbackSettings> globalSettingsSupplier,
		Supplier<ClickerXpSettings> globalClickSettingsSupplier,
		Supplier<CustomPatternLibrary> customPatternsSupplier,
		Runnable testAction,
		RemoteSessionManager sessionManager,
		SettingsLockService lockService,
		SettingsLockDraft lockDraft,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier lockSelectionEnabled)
	{
		this.profiles = profiles;
		this.clickProfiles = clickProfiles;
		this.settingsSink = settingsSink;
		this.globalSettingsSupplier = globalSettingsSupplier;
		this.globalClickSettingsSupplier = globalClickSettingsSupplier;
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
		minimumXpSpinner.setName("skillHapticMinimumXpGain");
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

		ClickerXpSettings globalClicks = globalClickSettingsSupplier.get();
		minimumClickXpSpinner = new JSpinner(new SpinnerNumberModel(
			globalClicks.getMinimumXpGain(),
			ClickerXpSettings.MINIMUM_XP_GAIN,
			ClickerXpSettings.MAXIMUM_XP_GAIN,
			1
		));
		minimumClickXpSpinner.setName("skillClickMinimumXpGain");
		xpClickSequenceComboBox.setName("skillClickXpSequence");
		levelUpClickSequenceComboBox.setName("skillClickLevelUpSequence");
		milestoneClickSequenceComboBox.setName("skillClickMilestoneSequence");
		PanelUi.setFixedWidth(minimumClickXpSpinner, PanelUi.NUMERIC_CONTROL_WIDTH);

		PanelUi.addPreferredHeightComponent(this, selectedSkillLabel);
		PanelUi.addPreferredHeightComponent(this, profileBlockHeader);
		PanelUi.addPreferredHeightComponent(this, useGlobalCheckBox);
		add(Box.createVerticalStrut(6));
		PanelUi.addPreferredHeightComponent(this, subsectionLabel("Haptic feedback"));

		JPanel thresholdRow = new JPanel(new BorderLayout(8, 0));
		thresholdRow.add(new JLabel("Minimum haptic XP gain"), BorderLayout.CENTER);
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

		add(Box.createVerticalStrut(6));
		PanelUi.addPreferredHeightComponent(this, subsectionLabel("Click feedback"));
		PanelUi.addPreferredHeightComponent(
			this,
			row("Minimum clicker XP gain", minimumClickXpSpinner)
		);
		PanelUi.addPreferredHeightComponent(this, row("XP gain", xpClickSequenceComboBox));
		PanelUi.addPreferredHeightComponent(
			this,
			row("Level-up", levelUpClickSequenceComboBox)
		);
		PanelUi.addPreferredHeightComponent(
			this,
			row("Milestone", milestoneClickSequenceComboBox)
		);

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
			this::selectedSkillUsesGlobalSettings
		);
		useGlobalCheckBox.addActionListener(event ->
		{
			if (!useGlobalLockBinding.handleAction(event))
			{
				toggleOverride();
			}
		});
		minimumXpSpinner.addChangeListener(event -> updateSelectedHapticProfile());
		intensitySlider.addChangeListener(event ->
		{
			intensityValueLabel.setText(intensitySlider.getValue() + "%");
			if (!intensitySlider.getValueIsAdjusting())
			{
				updateSelectedHapticProfile();
			}
		});
		patternComboBox.addActionListener(event ->
		{
			updateSelectedHapticProfile();
			updateControlState();
		});
		durationSpinner.addChangeListener(event -> updateSelectedHapticProfile());
		minimumClickXpSpinner.addChangeListener(event -> updateSelectedClickProfile());
		xpClickSequenceComboBox.addActionListener(event -> updateSelectedClickProfile());
		levelUpClickSequenceComboBox.addActionListener(event -> updateSelectedClickProfile());
		milestoneClickSequenceComboBox.addActionListener(event -> updateSelectedClickProfile());
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

	ClickerXpSettings getClickSettings(String skillId)
	{
		return clickProfiles.resolve(skillId, globalClickSettingsSupplier.get());
	}

	String getSelectedSkillId()
	{
		return selectedSkill == null ? null : selectedSkill.getId();
	}

	void applyDisplayedSettings(
		SkillFeedbackProfiles displayedProfiles,
		SkillClickProfiles displayedClickProfiles,
		CustomPatternLibrary library)
	{
		profiles = displayedProfiles.replaceMissingCustomPatterns(library);
		clickProfiles = displayedClickProfiles;
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
		if (selectedSkill == null)
		{
			return;
		}
		String skillId = selectedSkill.getId();
		if (!profiles.getOverride(skillId).isPresent()
			|| !clickProfiles.getOverride(skillId).isPresent())
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
			persistHapticProfiles(null);
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
		String skillId = selectedSkill.getId();
		if (useGlobalCheckBox.isSelected())
		{
			profiles = profiles.withoutOverride(skillId);
			clickProfiles = clickProfiles.withoutOverride(skillId);
		}
		else
		{
			profiles = profiles.withOverride(skillId, globalSettingsSupplier.get());
			clickProfiles = clickProfiles.withOverride(skillId, globalClickSettingsSupplier.get());
		}
		SettingsLockTarget target = SettingsLockCatalog.profileUsesGlobal(skillId);
		persistHapticProfiles(target);
		persistClickProfiles(target);
		loadSelectedProfile();
	}

	private void loadSelectedProfile()
	{
		if (selectedSkill == null)
		{
			return;
		}
		String skillId = selectedSkill.getId();
		XpFeedbackSettings override = profiles.getOverride(skillId).orElse(null);
		ClickerXpSettings clickOverride = clickProfiles.getOverride(skillId).orElse(null);
		selectedSkillLabel.setText(selectedSkill.getDisplayName());
		selectedSkillLabel.setToolTipText(
			"XP feedback settings for " + selectedSkill.getDisplayName()
		);
		XpFeedbackSettings displayed = override == null
			? globalSettingsSupplier.get()
			: override;
		ClickerXpSettings displayedClicks = clickOverride == null
			? globalClickSettingsSupplier.get()
			: clickOverride;

		updatingControls = true;
		try
		{
			useGlobalCheckBox.setSelected(override == null && clickOverride == null);
			minimumXpSpinner.setValue(displayed.getMinimumXpGain());
			intensitySlider.setValue(displayed.getIntensityPercent());
			intensityValueLabel.setText(displayed.getIntensityPercent() + "%");
			patternComboBox.setSelectedItem(displayed.getPatternSelection());
			durationSpinner.setValue(displayed.getDurationMillis());
			minimumClickXpSpinner.setValue(displayedClicks.getMinimumXpGain());
			xpClickSequenceComboBox.setSelectedItem(normalizeEnabled(
				displayedClicks.getXpGainSequence()
			));
			levelUpClickSequenceComboBox.setSelectedItem(displayedClicks.getLevelUpOverride());
			milestoneClickSequenceComboBox.setSelectedItem(
				displayedClicks.getMilestoneOverride()
			);
		}
		finally
		{
			updatingControls = false;
		}
		updateControlState();
	}

	private void updateSelectedHapticProfile()
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
		persistHapticProfiles(SettingsLockCatalog.profileBlock(selectedSkill.getId()));
	}

	private void updateSelectedClickProfile()
	{
		if (remoteReadOnly
			|| updatingControls
			|| selectedSkill == null
			|| profileBlockHeader.isEditLocked()
			|| useGlobalCheckBox.isSelected())
		{
			return;
		}
		clickProfiles = clickProfiles.withOverride(
			selectedSkill.getId(),
			new ClickerXpSettings(
				((Number) minimumClickXpSpinner.getValue()).intValue(),
				selected(xpClickSequenceComboBox, ClickSequence.ONE),
				selected(levelUpClickSequenceComboBox, ClickSequence.NONE),
				selected(milestoneClickSequenceComboBox, ClickSequence.NONE)
			)
		);
		persistClickProfiles(SettingsLockCatalog.profileBlock(selectedSkill.getId()));
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
		minimumClickXpSpinner.setEnabled(blockEditable && overridden);
		xpClickSequenceComboBox.setEnabled(blockEditable && overridden);
		levelUpClickSequenceComboBox.setEnabled(blockEditable && overridden);
		milestoneClickSequenceComboBox.setEnabled(blockEditable && overridden);
		testButton.setEnabled(editable && connected && previewAllowed);
	}

	private boolean selectedSkillUsesGlobalSettings()
	{
		if (selectedSkill == null)
		{
			return true;
		}
		String skillId = selectedSkill.getId();
		return !profiles.getOverride(skillId).isPresent()
			&& !clickProfiles.getOverride(skillId).isPresent();
	}

	private void persistHapticProfiles(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeSettingKeys.SKILL_FEEDBACK_PROFILES,
			profiles.toConfigValue()
		);
	}

	private void persistClickProfiles(SettingsLockTarget target)
	{
		settingsSink.set(
			target,
			HapticScapeSettingKeys.SKILL_CLICK_PROFILES,
			clickProfiles.toConfigValue()
		);
	}

	private static JLabel subsectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		return label;
	}

	private static JPanel row(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.CENTER);
		row.add(control, BorderLayout.EAST);
		return row;
	}

	private static ClickSequence normalizeEnabled(ClickSequence sequence)
	{
		return sequence != null && sequence.isEnabled() ? sequence : ClickSequence.ONE;
	}

	private static ClickSequence selected(
		JComboBox<ClickSequence> comboBox,
		ClickSequence fallback)
	{
		Object selected = comboBox.getSelectedItem();
		return selected instanceof ClickSequence ? (ClickSequence) selected : fallback;
	}
}
