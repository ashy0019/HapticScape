package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.remote.RemoteAction;
import com.ashy0019.hapticscape.remote.RemoteActionAcknowledgement;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Compact controller UI for immediate, consent-scoped remote actions. */
final class RemoteActionsPanel extends JPanel
{
	private static final int DEFAULT_INTENSITY_PERCENT = 50;
	private static final int DEFAULT_DURATION_MILLIS = 500;

	private final ActionDispatcher dispatcher;
	private CustomPatternLibrary customPatterns = CustomPatternLibrary.defaults();
	private final JComboBox<HapticPatternSelection> pattern =
		PanelUi.createPatternComboBox(() -> customPatterns);
	private final JSlider intensity = new JSlider(0, 100, DEFAULT_INTENSITY_PERCENT);
	private final JLabel intensityValue = new JLabel(DEFAULT_INTENSITY_PERCENT + "%");
	private final JSpinner duration = new JSpinner(new SpinnerNumberModel(
		DEFAULT_DURATION_MILLIS,
		RemotePermissions.MINIMUM_DURATION_MILLIS,
		RemotePermissions.MAXIMUM_DURATION_MILLIS,
		50
	));
	private final JButton buzzButton = new JButton("Send buzz");
	private final JButton stopButton = new JButton("Stop");
	private final JButton clickButton = new JButton("Play click");
	private final JTextArea message = new JTextArea(3, 16);
	private final JLabel messageLength = new JLabel(
		"0 / " + RemoteAction.MAXIMUM_MESSAGE_LENGTH
	);
	private final JCheckBox desktopNotification = new JCheckBox("Desktop notification", true);
	private final JCheckBox localChatbox = new JCheckBox("Local chatbox notice");
	private final JButton sendMessageButton = new JButton("Send message");
	private final SidebarTextLabel actionStatus = new SidebarTextLabel(
		"Ready for remote actions"
	);
	private boolean controllerValuesInitialized;
	private String lastRequestedActionId;
	private RemoteSessionSnapshot session = RemoteSessionSnapshot.local();
	private RemotePermissions permissions = RemotePermissions.defaults();

	RemoteActionsPanel(RemoteSessionManager sessionManager)
	{
		this(new ActionDispatcher()
		{
			@Override
			public String sendHaptic(String patternValue, int percent, int millis)
			{
				return sessionManager.sendRemoteHaptic(patternValue, percent, millis);
			}

			@Override
			public String sendClick()
			{
				return sessionManager.sendRemoteClick();
			}

			@Override
			public String sendMessage(
				String text,
				boolean desktop,
				boolean chatbox)
			{
				return sessionManager.sendRemoteMessage(text, desktop, chatbox);
			}

			@Override
			public String sendStop()
			{
				return sessionManager.stopRemoteOutput();
			}
		});
		apply(
			sessionManager.getSnapshot(),
			sessionManager.getPeerPermissions(),
			sessionManager.getControllerSettingsSnapshot()
		);
	}

	RemoteActionsPanel(ActionDispatcher dispatcher)
	{
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
		setName("remoteActionsPanel");
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("Live controls"));

		SidebarTextLabel explanation = new SidebarTextLabel(
			"Actions run only when the participant allows them. Their safety limits "
				+ "remain authoritative."
		);
		PanelUi.addVerticalComponent(this, explanation);

		pattern.setName("remotePattern");
		PanelUi.setFixedWidth(pattern, 118);
		PanelUi.addVerticalComponent(this, row("Pattern", pattern));

		intensity.setName("remoteIntensity");
		intensity.setPaintTicks(false);
		intensity.setPaintLabels(false);
		intensity.setPreferredSize(new Dimension(50, intensity.getPreferredSize().height));
		JPanel intensityRow = new JPanel(new BorderLayout(6, 0));
		intensityRow.add(new JLabel("Intensity"), BorderLayout.WEST);
		intensityRow.add(intensity, BorderLayout.CENTER);
		intensityRow.add(intensityValue, BorderLayout.EAST);
		allowHorizontalShrink(intensityRow);
		allowHorizontalShrink(intensity);
		PanelUi.addVerticalComponent(this, intensityRow);

		duration.setName("remoteDuration");
		PanelUi.setFixedWidth(duration, 70);
		duration.setToolTipText("Requested duration in milliseconds");
		PanelUi.addVerticalComponent(this, row("Duration", duration));

		buzzButton.setName("remoteBuzz");
		stopButton.setName("remoteStop");
		stopButton.setToolTipText("Stop remote haptic output immediately");
		configureCompactButton(buzzButton);
		configureCompactButton(stopButton);
		JPanel hapticButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		hapticButtons.add(buzzButton);
		hapticButtons.add(stopButton);
		allowHorizontalShrink(hapticButtons);
		PanelUi.addVerticalComponent(this, hapticButtons);

		clickButton.setName("remoteClick");
		configureCompactButton(clickButton);
		PanelUi.addVerticalComponent(this, clickButton);

		JPanel messageHeader = new JPanel(new BorderLayout(6, 0));
		messageHeader.add(new JLabel("Message"), BorderLayout.WEST);
		messageHeader.add(messageLength, BorderLayout.EAST);
		allowHorizontalShrink(messageHeader);
		PanelUi.addVerticalComponent(this, messageHeader);

		message.setName("remoteMessage");
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setToolTipText("Up to 200 characters; formatting and links are neutralized");
		((AbstractDocument) message.getDocument()).setDocumentFilter(
			new MaximumLengthFilter(RemoteAction.MAXIMUM_MESSAGE_LENGTH)
		);
		JScrollPane messageScroll = new JScrollPane(message);
		messageScroll.setName("remoteMessageScroll");
		allowHorizontalShrink(messageScroll);
		PanelUi.addVerticalComponent(this, messageScroll);

		desktopNotification.setName("remoteDesktopDestination");
		localChatbox.setName("remoteChatboxDestination");
		desktopNotification.setToolTipText("Show a local RuneLite desktop notification");
		localChatbox.setToolTipText(
			"Show a local-only HapticScape console line; nothing is sent to Jagex"
		);
		PanelUi.addVerticalComponent(this, desktopNotification);
		PanelUi.addVerticalComponent(this, localChatbox);

		sendMessageButton.setName("remoteSendMessage");
		configureCompactButton(sendMessageButton);
		PanelUi.addVerticalComponent(this, sendMessageButton);

		actionStatus.setName("remoteActionStatus");
		actionStatus.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		PanelUi.addVerticalComponent(this, actionStatus);

		intensity.addChangeListener(event ->
			intensityValue.setText(intensity.getValue() + "%"));
		pattern.addActionListener(event -> updatePatternTooltip());
		message.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				updateMessageState();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				updateMessageState();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				updateMessageState();
			}
		});
		desktopNotification.addActionListener(event -> updateEnabledState());
		localChatbox.addActionListener(event -> updateEnabledState());
		buzzButton.addActionListener(event -> sendBuzz());
		stopButton.addActionListener(event -> sendStop());
		clickButton.addActionListener(event -> sendClick());
		sendMessageButton.addActionListener(event -> sendMessage());

		apply(RemoteSessionSnapshot.local(), RemotePermissions.defaults(), null);
	}

	void apply(
		RemoteSessionSnapshot updatedSession,
		RemotePermissions updatedPermissions,
		RemoteSettingsSnapshot settings)
	{
		session = updatedSession;
		permissions = updatedPermissions;
		boolean controllerSession = session.getRole() == RemoteRole.CONTROLLER
			&& session.getState() != RemoteSessionState.LOCAL;
		setVisible(controllerSession && isConnectedState(session.getState()));

		if (!controllerSession)
		{
			controllerValuesInitialized = false;
			lastRequestedActionId = null;
			message.setText("");
			actionStatus.setPlainText("Ready for remote actions");
		}

		if (controllerSession && settings != null)
		{
			XpFeedbackSettings global = settings.getGlobalXpFeedbackSettings();
			HapticPatternSelection selected = controllerValuesInitialized
				? (HapticPatternSelection) pattern.getSelectedItem()
				: global.getPatternSelection();
			customPatterns = settings.getCustomPatterns();
			PanelUi.setPatternChoices(pattern, selected, customPatterns);
			updatePatternTooltip();
			if (!controllerValuesInitialized)
			{
				intensity.setValue(Math.min(
					global.getIntensityPercent(),
					permissions.getMaximumIntensityPercent()
				));
				duration.setValue(Math.min(
					global.getDurationMillis(),
					permissions.getMaximumDurationMillis()
				));
				controllerValuesInitialized = true;
			}
		}

		applySafetyCaps();
		updateEnabledState();
		revalidate();
		repaint();
	}

	void showAcknowledgement(RemoteActionAcknowledgement acknowledgement)
	{
		if (lastRequestedActionId == null
			|| !lastRequestedActionId.equals(acknowledgement.getActionId()))
		{
			return;
		}
		String details = acknowledgement.getMessage();
		if (acknowledgement.getAppliedDurationMillis() > 0)
		{
			details += " (" + acknowledgement.getAppliedIntensityPercent()
				+ "%, " + acknowledgement.getAppliedDurationMillis() + " ms)";
		}
		actionStatus.setPlainText(
			resultName(acknowledgement) + ": " + details
		);
	}

	private void applySafetyCaps()
	{
		int maximumIntensity = permissions.getMaximumIntensityPercent();
		intensity.setMaximum(maximumIntensity);
		if (intensity.getValue() > maximumIntensity)
		{
			intensity.setValue(maximumIntensity);
		}

		SpinnerNumberModel durationModel = (SpinnerNumberModel) duration.getModel();
		durationModel.setMaximum(permissions.getMaximumDurationMillis());
		if (((Number) duration.getValue()).intValue() > permissions.getMaximumDurationMillis())
		{
			duration.setValue(permissions.getMaximumDurationMillis());
		}
	}

	private void updateMessageState()
	{
		messageLength.setText(
			message.getDocument().getLength() + " / " + RemoteAction.MAXIMUM_MESSAGE_LENGTH
		);
		updateEnabledState();
	}

	private void updatePatternTooltip()
	{
		HapticPatternSelection selected = (HapticPatternSelection) pattern.getSelectedItem();
		pattern.setToolTipText(
			selected == null ? null : selected.getDisplayName(customPatterns)
		);
	}

	private void updateEnabledState()
	{
		boolean active = session.getRole() == RemoteRole.CONTROLLER
			&& session.getState() == RemoteSessionState.ACTIVE;
		boolean mayHaptic = active && permissions.isHapticsAllowed();
		boolean mayClick = active && permissions.isClicksAllowed();
		boolean mayDesktop = active && permissions.isDesktopNotificationsAllowed();
		boolean mayChatbox = active && permissions.isLocalChatboxMessagesAllowed();

		pattern.setEnabled(mayHaptic);
		intensity.setEnabled(mayHaptic);
		duration.setEnabled(mayHaptic);
		buzzButton.setEnabled(mayHaptic);
		buzzButton.setToolTipText(permissionTooltip(
			active,
			permissions.isHapticsAllowed(),
			"haptic actions"
		));

		clickButton.setEnabled(mayClick);
		clickButton.setToolTipText(permissionTooltip(
			active,
			permissions.isClicksAllowed(),
			"click sounds"
		));

		stopButton.setEnabled(session.getRole() == RemoteRole.CONTROLLER
			&& isConnectedState(session.getState()));
		message.setEnabled(mayDesktop || mayChatbox);
		desktopNotification.setEnabled(mayDesktop);
		localChatbox.setEnabled(mayChatbox);
		if (!permissions.isDesktopNotificationsAllowed())
		{
			desktopNotification.setSelected(false);
		}
		if (!permissions.isLocalChatboxMessagesAllowed())
		{
			localChatbox.setSelected(false);
		}
		if (!desktopNotification.isSelected() && !localChatbox.isSelected())
		{
			if (mayDesktop)
			{
				desktopNotification.setSelected(true);
			}
			else if (mayChatbox)
			{
				localChatbox.setSelected(true);
			}
		}
		sendMessageButton.setEnabled(
			active
				&& !message.getText().trim().isEmpty()
				&& (desktopNotification.isSelected() || localChatbox.isSelected())
		);
		sendMessageButton.setToolTipText(
			mayDesktop || mayChatbox
				? null
				: permissionTooltip(active, false, "remote messages")
		);
	}

	private void sendBuzz()
	{
		HapticPatternSelection selected = (HapticPatternSelection) pattern.getSelectedItem();
		if (selected == null)
		{
			return;
		}
		sendAction("Sending haptic action...", () -> dispatcher.sendHaptic(
			selected.toConfigValue(),
			intensity.getValue(),
			((Number) duration.getValue()).intValue()
		));
	}

	private void sendStop()
	{
		sendAction("Sending stop command...", dispatcher::sendStop);
	}

	private void sendClick()
	{
		sendAction("Sending click...", dispatcher::sendClick);
	}

	private void sendMessage()
	{
		if (sendAction("Sending message...", () -> dispatcher.sendMessage(
			message.getText(),
			desktopNotification.isSelected(),
			localChatbox.isSelected()
		)))
		{
			message.setText("");
		}
	}

	private boolean sendAction(String pendingMessage, ActionSender sender)
	{
		try
		{
			actionStatus.setPlainText(pendingMessage);
			lastRequestedActionId = sender.send();
			actionStatus.setPlainText("Sent; awaiting participant acknowledgement");
			return true;
		}
		catch (RuntimeException failure)
		{
			lastRequestedActionId = null;
			actionStatus.setPlainText(
				"Not sent: " + (failure.getMessage() == null
					? "Remote action failed"
					: failure.getMessage())
			);
			return false;
		}
	}

	private static String resultName(RemoteActionAcknowledgement acknowledgement)
	{
		switch (acknowledgement.getResult())
		{
			case EXECUTED:
				return "Delivered";
			case LIMITED:
				return "Limited";
			case DENIED:
				return "Denied";
			case PAUSED:
				return "Paused";
			case EXPIRED:
				return "Expired";
			case RATE_LIMITED:
				return "Rate limited";
			case INVALID:
				return "Rejected";
			case FAILED:
			default:
				return "Failed";
		}
	}

	private static boolean isConnectedState(RemoteSessionState state)
	{
		return state == RemoteSessionState.ACTIVE
			|| state == RemoteSessionState.PEER_EMERGENCY_PAUSED;
	}

	private static String permissionTooltip(
		boolean active,
		boolean allowed,
		String action)
	{
		if (!active)
		{
			return "The participant is not ready for remote actions";
		}
		return allowed ? null : "The participant has not allowed " + action;
	}

	private static JPanel row(String name, javax.swing.JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.add(new JLabel(name), BorderLayout.WEST);
		row.add(control, BorderLayout.EAST);
		allowHorizontalShrink(row);
		return row;
	}

	private static void configureCompactButton(AbstractButton button)
	{
		button.setMargin(new java.awt.Insets(2, 5, 2, 5));
		allowHorizontalShrink(button);
	}

	private static void allowHorizontalShrink(javax.swing.JComponent component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setMinimumSize(new Dimension(0, preferred.height));
	}

	@FunctionalInterface
	private interface ActionSender
	{
		String send();
	}

	interface ActionDispatcher
	{
		String sendHaptic(String patternValue, int intensityPercent, int durationMillis);

		String sendClick();

		String sendMessage(String message, boolean desktop, boolean localChatbox);

		String sendStop();
	}

	private static final class MaximumLengthFilter extends DocumentFilter
	{
		private final int maximumLength;

		private MaximumLengthFilter(int maximumLength)
		{
			this.maximumLength = maximumLength;
		}

		@Override
		public void insertString(
			FilterBypass bypass,
			int offset,
			String text,
			AttributeSet attributes) throws BadLocationException
		{
			replace(bypass, offset, 0, text, attributes);
		}

		@Override
		public void replace(
			FilterBypass bypass,
			int offset,
			int length,
			String text,
			AttributeSet attributes) throws BadLocationException
		{
			String replacement = text == null ? "" : text;
			int available = maximumLength - (bypass.getDocument().getLength() - length);
			if (available <= 0)
			{
				return;
			}
			bypass.replace(
				offset,
				length,
				replacement.substring(0, Math.min(available, replacement.length())),
				attributes
			);
		}
	}
}
