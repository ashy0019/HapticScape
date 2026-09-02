package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.update.HapticScapeVersion;
import com.ashy0019.hapticscape.update.UpdateCheckResult;
import com.ashy0019.hapticscape.update.UpdateCheckService;
import com.ashy0019.hapticscape.update.UpdatePreferences;
import com.ashy0019.hapticscape.update.UpdatePreferencesStore;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

final class UpdatesPanel extends JPanel
{
	private final UpdatePreferencesStore preferencesStore;
	private final UpdateCheckService checkService;
	private final JCheckBox automaticUpdates =
		new JCheckBox("Install updates automatically");
	private final JCheckBox updateNotifications =
		new JCheckBox("Notify me when updates are available");
	private final JButton checkNowButton = new JButton("Check now");
	private final JLabel statusLabel = new JLabel("Loading update preferences...", SwingConstants.CENTER);
	private boolean applyingPreferences;

	UpdatesPanel(
		UpdatePreferencesStore preferencesStore,
		UpdateCheckService checkService)
	{
		this.preferencesStore = preferencesStore;
		this.checkService = checkService;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		automaticUpdates.setEnabled(false);
		updateNotifications.setEnabled(false);
		checkNowButton.setEnabled(false);

		JPanel choices = new JPanel(new GridLayout(0, 1, 0, 2));
		choices.setBorder(BorderFactory.createTitledBorder("Startup behavior"));
		choices.add(automaticUpdates);
		choices.add(updateNotifications);
		PanelUi.addVerticalComponent(this, choices);

		JLabel privacy = new JLabel(
			"With both options off, startup does not contact GitHub.");
		privacy.setToolTipText(
			"Check now only requests public release metadata; it does not send game or account data");
		PanelUi.addVerticalComponent(this, privacy);

		JPanel currentVersion = new JPanel(new BorderLayout(6, 0));
		currentVersion.add(new JLabel("Installed version"), BorderLayout.WEST);
		currentVersion.add(new JLabel(HapticScapeVersion.current()), BorderLayout.EAST);
		PanelUi.addVerticalComponent(this, currentVersion);
		PanelUi.addVerticalComponent(this, checkNowButton);
		PanelUi.addVerticalComponent(this, statusLabel);

		automaticUpdates.addActionListener(event -> saveSelections());
		updateNotifications.addActionListener(event -> saveSelections());
		checkNowButton.addActionListener(event -> checkNow());

		preferencesStore.load().whenComplete((preferences, failure) ->
			SwingUtilities.invokeLater(() -> applyPreferences(preferences)));
	}

	private void applyPreferences(UpdatePreferences preferences)
	{
		UpdatePreferences resolved = preferences == null
			? UpdatePreferences.defaults()
			: preferences;
		applyingPreferences = true;
		try
		{
			automaticUpdates.setSelected(resolved.isAutomaticUpdates());
			updateNotifications.setSelected(resolved.isUpdateNotifications());
		}
		finally
		{
			applyingPreferences = false;
		}
		automaticUpdates.setEnabled(true);
		updateNotifications.setEnabled(true);
		checkNowButton.setEnabled(true);
		statusLabel.setText("Update preferences ready");
	}

	private void saveSelections()
	{
		if (applyingPreferences)
		{
			return;
		}
		UpdatePreferences current = preferencesStore.getCurrent();
		UpdatePreferences updated = current
			.withAutomaticUpdates(automaticUpdates.isSelected())
			.withUpdateNotifications(updateNotifications.isSelected());
		preferencesStore.save(updated);
		statusLabel.setText("Preferences saved");
	}

	void checkNow()
	{
		if (!checkNowButton.isEnabled())
		{
			statusLabel.setText("Update preferences are still loading");
			return;
		}
		checkNowButton.setEnabled(false);
		statusLabel.setText("Checking GitHub...");
		checkService.check(HapticScapeVersion.current(), result ->
			SwingUtilities.invokeLater(() -> applyCheckResult(result)));
	}

	private void applyCheckResult(UpdateCheckResult result)
	{
		checkNowButton.setEnabled(true);
		if (result.isFailure())
		{
			statusLabel.setText(result.getErrorMessage());
			return;
		}
		if (result.isNewer())
		{
			preferencesStore.save(
				preferencesStore.getCurrent().requestUpdateOnNextLaunch());
			statusLabel.setText(
				"Version " + result.getLatestVersion() + " found — restart to install");
			return;
		}
		if ("development".equals(HapticScapeVersion.current()))
		{
			statusLabel.setText("Latest release: " + result.getLatestVersion());
			return;
		}
		statusLabel.setText("HapticScape is up to date");
	}
}
