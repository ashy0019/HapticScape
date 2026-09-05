package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.remote.RemoteInvitation;
import com.ashy0019.hapticscape.remote.RemotePairingCode;
import com.ashy0019.hapticscape.remote.RemotePairingService;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

/** Owns invitation abstraction, temporary pairing codes, and direct-code fallback UI. */
final class RemotePairingPanel extends JPanel
{
	private final HapticScapeConfig config;
	private final ConfigManager configManager;
	private final RemoteSessionManager sessionManager;
	private final RemotePairingService pairingService;
	private final Consumer<String> statusSink;
	private final Consumer<String> errorSink;
	private final JTextField relayUrlField = new JTextField();
	private final JPanel relaySettingsPanel = new JPanel(new BorderLayout(8, 0));
	private final JButton connectionSettingsButton = new JButton("Connection settings...");
	private final JTextField connectionCodeOutput = new JTextField();
	private final JTextField connectionCodeInput = new JTextField();
	private final JButton createButton = new JButton("Create & copy code");
	private final JButton copyButton = new JButton("Copy again");
	private final JButton pasteButton = new JButton("Paste & join");
	private final JButton joinButton = new JButton("Join entered code");
	private final JPanel controllerPanel = new JPanel();
	private final JPanel participantPanel = new JPanel();

	private boolean wasLocal = true;
	private boolean pairingBusy;
	private boolean connectionSettingsExpanded;
	private long pairingAttempt;
	private RemotePairingCode activePairingCode;
	private String activePairingRelayUrl;

	RemotePairingPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		RemoteSessionManager sessionManager,
		RemotePairingService pairingService,
		Consumer<String> statusSink,
		Consumer<String> errorSink)
	{
		this.config = Objects.requireNonNull(config, "config");
		this.configManager = Objects.requireNonNull(configManager, "configManager");
		this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
		this.pairingService = Objects.requireNonNull(pairingService, "pairingService");
		this.statusSink = Objects.requireNonNull(statusSink, "statusSink");
		this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder());

		configureRelaySettings();
		configureControllerPanel();
		configureParticipantPanel();

		PanelUi.addVerticalComponent(this, controllerPanel);
		PanelUi.addVerticalComponent(this, participantPanel);
		PanelUi.addVerticalComponent(this, connectionSettingsButton);
		PanelUi.addVerticalComponent(this, relaySettingsPanel);
		refreshRelaySettingsVisibility(false);

		createButton.addActionListener(event -> createConnectionCode());
		copyButton.addActionListener(event -> copyConnectionCode());
		pasteButton.addActionListener(event -> pasteAndJoinConnection());
		joinButton.addActionListener(event -> joinConnection());
		connectionCodeInput.addActionListener(event -> joinConnection());
		connectionSettingsButton.addActionListener(event ->
		{
			connectionSettingsExpanded = !connectionSettingsExpanded;
			refreshRelaySettingsVisibility(true);
		});
	}

	void apply(RemoteSessionSnapshot snapshot)
	{
		boolean local = snapshot.getState() == RemoteSessionState.LOCAL;
		boolean controller = snapshot.getRole() == RemoteRole.CONTROLLER && !local;
		if (local && !wasLocal)
		{
			pairingAttempt++;
			pairingBusy = false;
			cancelActivePairing();
			connectionCodeOutput.setText("");
			connectionCodeInput.setText("");
		}
		wasLocal = local;

		refreshConnectionControls();
		controllerPanel.setVisible(local || controller
			&& (snapshot.getState() == RemoteSessionState.CONNECTING
				|| snapshot.getState() == RemoteSessionState.WAITING_FOR_PEER));
		participantPanel.setVisible(local);
		connectionSettingsButton.setVisible(local);
		refreshRelaySettingsVisibility(false);
		revalidate();
		repaint();
	}

	void close()
	{
		pairingAttempt++;
		cancelActivePairing();
	}

	private void configureRelaySettings()
	{
		String configuredRelay = HapticScapeConfig.resolveRemoteRelayUrl(
			config.remoteRelayUrl()
		);
		relayUrlField.setText(configuredRelay);
		relayUrlField.setToolTipText(
			"Hosted HapticScape relay by default; replace this URL to use a self-hosted relay"
		);
		connectionSettingsExpanded = !HapticScapeConfig.DEFAULT_REMOTE_RELAY_URL.equals(
			configuredRelay
		);
		configureCompactButton(connectionSettingsButton);
		relaySettingsPanel.add(new JLabel("Relay"), BorderLayout.WEST);
		relaySettingsPanel.add(relayUrlField, BorderLayout.CENTER);
		allowHorizontalShrink(relaySettingsPanel);
	}

	private void configureControllerPanel()
	{
		controllerPanel.setLayout(new BoxLayout(controllerPanel, BoxLayout.Y_AXIS));
		controllerPanel.setBorder(BorderFactory.createTitledBorder("Control a partner"));
		PanelUi.addVerticalComponent(controllerPanel, new SidebarTextLabel(
			"Create a temporary encrypted connection code and send it privately to your partner."
		));
		JPanel createRow = new JPanel(new GridLayout(0, 1, 0, 4));
		allowHorizontalShrink(createRow);
		configureCompactButton(createButton);
		configureCompactButton(copyButton);
		createRow.add(createButton);
		createRow.add(copyButton);
		PanelUi.addVerticalComponent(controllerPanel, createRow);
		connectionCodeOutput.setEditable(false);
		connectionCodeOutput.setToolTipText(
			"The code expires after five minutes and can be redeemed only once"
		);
		allowHorizontalShrink(connectionCodeOutput);
		PanelUi.addVerticalComponent(controllerPanel, connectionCodeOutput);
		allowHorizontalShrink(controllerPanel);
	}

	private void configureParticipantPanel()
	{
		participantPanel.setLayout(new BoxLayout(participantPanel, BoxLayout.Y_AXIS));
		participantPanel.setBorder(BorderFactory.createTitledBorder("Let a partner control you"));
		PanelUi.addVerticalComponent(participantPanel, new SidebarTextLabel(
			"Paste the temporary connection code your partner sent you."
		));
		connectionCodeInput.setToolTipText(
			"Compact HSP1 connection codes and legacy HSR1 invitations are accepted"
		);
		allowHorizontalShrink(connectionCodeInput);
		PanelUi.addVerticalComponent(participantPanel, connectionCodeInput);
		JPanel joinRow = new JPanel(new GridLayout(0, 1, 0, 4));
		allowHorizontalShrink(joinRow);
		configureCompactButton(pasteButton);
		configureCompactButton(joinButton);
		joinRow.add(pasteButton);
		joinRow.add(joinButton);
		PanelUi.addVerticalComponent(participantPanel, joinRow);
		allowHorizontalShrink(participantPanel);
	}

	private void createConnectionCode()
	{
		String relayUrl = relayUrlField.getText().trim();
		if (relayUrl.isEmpty())
		{
			errorSink.accept("Enter the wss:// URL of your HapticScape relay first.");
			return;
		}
		try
		{
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.REMOTE_RELAY_URL_KEY,
				relayUrl
			);
			pairingBusy = true;
			long attempt = ++pairingAttempt;
			statusSink.accept("Creating secure connection code...");
			RemoteInvitation invitation = sessionManager.startController(relayUrl);
			pairingService.publish(invitation).whenComplete((code, error) ->
				SwingUtilities.invokeLater(() -> finishPublishingCode(
					attempt,
					relayUrl,
					invitation,
					code,
					error
				))
			);
		}
		catch (RuntimeException exception)
		{
			pairingBusy = false;
			errorSink.accept(exception.getMessage());
		}
	}

	private void finishPublishingCode(
		long attempt,
		String relayUrl,
		RemoteInvitation invitation,
		RemotePairingCode code,
		Throwable error)
	{
		if (attempt != pairingAttempt || !sessionManager.isControllerSession())
		{
			if (code != null)
			{
				pairingService.cancel(relayUrl, code);
			}
			return;
		}

		pairingBusy = false;
		if (error == null)
		{
			activePairingCode = code;
			activePairingRelayUrl = relayUrl;
			showOutput(code.encode());
			statusSink.accept(copyToClipboard(code.encode())
				? "Connection code copied. Waiting for your partner..."
				: "Connection code ready. Copy it and send it to your partner."
			);
		}
		else
		{
			String directCode = invitation.encode();
			showOutput(directCode);
			statusSink.accept(copyToClipboard(directCode)
				? "Pairing service unavailable. A direct connection code was copied."
				: "Pairing service unavailable. Copy the direct connection code manually."
			);
		}
		copyButton.setEnabled(!connectionCodeOutput.getText().trim().isEmpty());
	}

	private void joinConnection()
	{
		String encoded = connectionCodeInput.getText().trim();
		if (encoded.isEmpty())
		{
			errorSink.accept("Paste a HapticScape connection code first.");
			return;
		}

		if (encoded.startsWith("HSP1."))
		{
			joinPairingCode(encoded);
			return;
		}

		try
		{
			RemoteInvitation invitation = RemoteInvitation.parse(encoded);
			sessionManager.validateParticipantJoin();
			if (confirmRemoteControl(invitation.getRelayUrl()))
			{
				sessionManager.joinParticipant(encoded);
			}
		}
		catch (RuntimeException exception)
		{
			errorSink.accept(exception.getMessage());
		}
	}

	private void joinPairingCode(String encoded)
	{
		String relayUrl = relayUrlField.getText().trim();
		try
		{
			RemotePairingCode.parse(encoded);
			sessionManager.validateParticipantJoin();
			if (relayUrl.isEmpty())
			{
				throw new IllegalArgumentException("Enter the relay URL used by your partner.");
			}
			if (!confirmRemoteControl(relayUrl))
			{
				return;
			}
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.REMOTE_RELAY_URL_KEY,
				relayUrl
			);
			pairingBusy = true;
			long attempt = ++pairingAttempt;
			refreshConnectionControls();
			statusSink.accept("Retrieving secure connection...");
			pairingService.redeem(relayUrl, encoded).whenComplete((invitation, error) ->
				SwingUtilities.invokeLater(() -> finishRedeemingCode(
					attempt,
					invitation,
					error
				))
			);
		}
		catch (RuntimeException exception)
		{
			pairingBusy = false;
			refreshConnectionControls();
			errorSink.accept(exception.getMessage());
		}
	}

	private void finishRedeemingCode(
		long attempt,
		RemoteInvitation invitation,
		Throwable error)
	{
		if (attempt != pairingAttempt)
		{
			return;
		}
		pairingBusy = false;
		if (error != null)
		{
			refreshConnectionControls();
			errorSink.accept(rootMessage(error));
			return;
		}
		try
		{
			sessionManager.joinParticipant(invitation.encode());
		}
		catch (RuntimeException exception)
		{
			refreshConnectionControls();
			errorSink.accept(exception.getMessage());
		}
	}

	private boolean confirmRemoteControl(String relayUrl)
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"<html>Join Remote Control through:<br><b>"
				+ relayUrl
				+ "</b><br><br>The controller will become authoritative for "
				+ "HapticScape feedback settings during the session.<br>"
				+ "Your current settings will seed their controls. Accepted changes "
				+ "are saved here and remain after the session.<br>"
				+ "Remote actions are limited by the permissions shown on this page.<br>"
				+ "Emergency Off and End Session always remain local.<br><br>"
				+ "The relay operator can see your network IP. HapticScape does not "
				+ "send your IP to the paired client.</html>",
			"Accept Remote Control",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		return choice == JOptionPane.YES_OPTION;
	}

	private void refreshConnectionControls()
	{
		RemoteSessionSnapshot snapshot = sessionManager.getSnapshot();
		boolean local = snapshot.getState() == RemoteSessionState.LOCAL;
		boolean controller = snapshot.getRole() == RemoteRole.CONTROLLER && !local;
		boolean locallyAvailable = local && !pairingBusy;
		createButton.setEnabled(locallyAvailable);
		joinButton.setEnabled(locallyAvailable);
		relayUrlField.setEnabled(locallyAvailable);
		connectionCodeInput.setEnabled(locallyAvailable);
		pasteButton.setEnabled(locallyAvailable);
		copyButton.setEnabled(
			controller && !connectionCodeOutput.getText().trim().isEmpty()
		);
		connectionSettingsButton.setEnabled(locallyAvailable);
	}

	private void refreshRelaySettingsVisibility(boolean layout)
	{
		boolean local = sessionManager.getSnapshot().getState() == RemoteSessionState.LOCAL;
		relaySettingsPanel.setVisible(local && connectionSettingsExpanded);
		connectionSettingsButton.setText(connectionSettingsExpanded
			? "Hide connection settings"
			: "Connection settings..."
		);
		if (layout)
		{
			revalidate();
			repaint();
		}
	}

	private void copyConnectionCode()
	{
		String code = connectionCodeOutput.getText().trim();
		if (!code.isEmpty() && copyToClipboard(code))
		{
			statusSink.accept("Connection code copied");
		}
	}

	private void pasteAndJoinConnection()
	{
		try
		{
			Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
			if (value instanceof String)
			{
				connectionCodeInput.setText(((String) value).trim());
				connectionCodeInput.setCaretPosition(0);
				joinConnection();
			}
		}
		catch (Exception exception)
		{
			errorSink.accept("Could not paste a connection code from the clipboard.");
		}
	}

	private void showOutput(String code)
	{
		connectionCodeOutput.setText(code);
		connectionCodeOutput.setCaretPosition(0);
	}

	private boolean copyToClipboard(String value)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(value),
				null
			);
			return true;
		}
		catch (RuntimeException exception)
		{
			errorSink.accept("Could not copy the connection code to the clipboard.");
			return false;
		}
	}

	private void cancelActivePairing()
	{
		RemotePairingCode code = activePairingCode;
		String relayUrl = activePairingRelayUrl;
		activePairingCode = null;
		activePairingRelayUrl = null;
		if (code != null && relayUrl != null)
		{
			pairingService.cancel(relayUrl, code);
		}
	}

	private static String rootMessage(Throwable error)
	{
		Throwable current = error;
		while (current.getCause() != null)
		{
			current = current.getCause();
		}
		return current.getMessage() == null
			? "Remote pairing failed"
			: current.getMessage();
	}

	private static void configureCompactButton(JButton button)
	{
		button.setMargin(new java.awt.Insets(2, 6, 2, 6));
		allowHorizontalShrink(button);
	}

	private static void allowHorizontalShrink(JComponent component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setMinimumSize(new Dimension(0, preferred.height));
	}
}
