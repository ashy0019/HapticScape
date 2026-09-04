package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Compact controls which remain owned by the participant at all times. */
final class RemotePermissionsPanel extends JPanel
{
	private final RemoteSessionManager sessionManager;
	private final JCheckBox settings = new JCheckBox("Settings changes");
	private final JCheckBox haptics = new JCheckBox("Haptic actions");
	private final JCheckBox clicks = new JCheckBox("Click sounds");
	private final JCheckBox notifications = new JCheckBox("Desktop notifications");
	private final JCheckBox chatbox = new JCheckBox("Local chatbox notices");
	private final JSlider maximumIntensity = new JSlider(0, 100);
	private final JLabel maximumIntensityValue = new JLabel();
	private final JSpinner maximumDuration = new JSpinner(new SpinnerNumberModel(
		3_000,
		RemotePermissions.MINIMUM_DURATION_MILLIS,
		RemotePermissions.MAXIMUM_DURATION_MILLIS,
		50
	));
	private boolean applying;

	RemotePermissionsPanel(RemoteSessionManager sessionManager)
	{
		this.sessionManager = sessionManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("Your remote permissions"));

		SidebarTextLabel explanation = new SidebarTextLabel(
			"Only you can change these controls. They cannot be locked or changed remotely."
		);
		PanelUi.addVerticalComponent(this, explanation);
		settings.setToolTipText("Allow the controller to change feedback settings");
		haptics.setToolTipText("Allow bounded haptic requests");
		clicks.setToolTipText("Allow the controller to play your local click sound");
		notifications.setToolTipText("Allow local RuneLite desktop notifications");
		chatbox.setToolTipText(
			"Show a local-only HapticScape console line; nothing is sent to game chat"
		);
		PanelUi.addVerticalComponent(this, settings);
		PanelUi.addVerticalComponent(this, haptics);
		PanelUi.addVerticalComponent(this, clicks);
		PanelUi.addVerticalComponent(this, notifications);
		PanelUi.addVerticalComponent(this, chatbox);

		maximumIntensity.setPaintTicks(false);
		maximumIntensity.setPaintLabels(false);
		maximumIntensity.setPreferredSize(new Dimension(
			50,
			maximumIntensity.getPreferredSize().height
		));
		JPanel intensityRow = new JPanel(new BorderLayout(6, 0));
		intensityRow.add(new JLabel("Max intensity"), BorderLayout.WEST);
		intensityRow.add(maximumIntensity, BorderLayout.CENTER);
		intensityRow.add(maximumIntensityValue, BorderLayout.EAST);
		allowHorizontalShrink(intensityRow);
		allowHorizontalShrink(maximumIntensity);
		PanelUi.addVerticalComponent(this, intensityRow);

		JPanel durationRow = new JPanel(new BorderLayout(6, 0));
		PanelUi.setFixedWidth(maximumDuration, 70);
		maximumDuration.setToolTipText("Maximum duration in milliseconds");
		durationRow.add(new JLabel("Max duration"), BorderLayout.WEST);
		durationRow.add(maximumDuration, BorderLayout.CENTER);
		allowHorizontalShrink(durationRow);
		PanelUi.addVerticalComponent(this, durationRow);

		settings.addActionListener(event -> save());
		haptics.addActionListener(event -> save());
		clicks.addActionListener(event -> save());
		notifications.addActionListener(event -> save());
		chatbox.addActionListener(event -> save());
		maximumIntensity.addChangeListener(event ->
		{
			maximumIntensityValue.setText(maximumIntensity.getValue() + "%");
			if (!maximumIntensity.getValueIsAdjusting())
			{
				save();
			}
		});
		maximumDuration.addChangeListener(event -> save());
		apply(sessionManager.getVisiblePermissions());
	}

	void apply(RemotePermissions permissions)
	{
		applying = true;
		try
		{
			settings.setSelected(permissions.isSettingsAllowed());
			haptics.setSelected(permissions.isHapticsAllowed());
			clicks.setSelected(permissions.isClicksAllowed());
			notifications.setSelected(permissions.isDesktopNotificationsAllowed());
			chatbox.setSelected(permissions.isLocalChatboxMessagesAllowed());
			maximumIntensity.setValue(permissions.getMaximumIntensityPercent());
			maximumIntensityValue.setText(permissions.getMaximumIntensityPercent() + "%");
			maximumDuration.setValue(permissions.getMaximumDurationMillis());
		}
		finally
		{
			applying = false;
		}
	}

	private void save()
	{
		if (applying)
		{
			return;
		}
		sessionManager.updateLocalPermissions(new RemotePermissions(
			settings.isSelected(),
			haptics.isSelected(),
			clicks.isSelected(),
			notifications.isSelected(),
			chatbox.isSelected(),
			maximumIntensity.getValue(),
			((Number) maximumDuration.getValue()).intValue()
		));
	}

	private static void allowHorizontalShrink(javax.swing.JComponent component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setMinimumSize(new Dimension(0, preferred.height));
	}
}
