package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.remote.RemoteInvitation;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

final class RemoteControlPanel extends JPanel implements RemoteSessionListener
{
	private final ConfigManager configManager;
	private final RemoteSessionManager sessionManager;
	private final JLabel statusLabel = new JLabel("Local control");
	private final JTextField relayUrlField = new JTextField();
	private final JTextArea invitationOutput = new JTextArea(4, 24);
	private final JTextArea invitationInput = new JTextArea(4, 24);
	private final JButton createButton = new JButton("Create invitation");
	private final JButton joinButton = new JButton("Join invitation");
	private final JButton emergencyButton = new JButton("EMERGENCY OFF");
	private final JButton resumeButton = new JButton("Resume");
	private final JButton endButton = new JButton("End session");

	RemoteControlPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		RemoteSessionManager sessionManager)
	{
		this.configManager = configManager;
		this.sessionManager = sessionManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		JLabel privacy = new JLabel(
			"Peers do not connect directly. The relay operator can see each client's IP."
		);
		privacy.setToolTipText(
			"Settings are encrypted before relay transport. If your partner operates the relay, they may be able to see connection metadata such as your IP address."
		);
		PanelUi.addVerticalComponent(this, privacy);

		relayUrlField.setText(config.remoteRelayUrl());
		PanelUi.addVerticalComponent(this, row("Relay", relayUrlField));

		JPanel controller = new JPanel();
		controller.setLayout(new BoxLayout(controller, BoxLayout.Y_AXIS));
		controller.setBorder(BorderFactory.createTitledBorder("Controller"));
		PanelUi.addVerticalComponent(controller, createButton);
		invitationOutput.setEditable(false);
		invitationOutput.setLineWrap(true);
		invitationOutput.setWrapStyleWord(true);
		invitationOutput.setToolTipText(
			"Share this invitation privately with the participant. It contains the session encryption key."
		);
		PanelUi.addVerticalComponent(controller, new JScrollPane(invitationOutput));
		PanelUi.addVerticalComponent(this, controller);

		JPanel participant = new JPanel();
		participant.setLayout(new BoxLayout(participant, BoxLayout.Y_AXIS));
		participant.setBorder(BorderFactory.createTitledBorder("Participant"));
		invitationInput.setLineWrap(true);
		invitationInput.setWrapStyleWord(true);
		PanelUi.addVerticalComponent(participant, new JScrollPane(invitationInput));
		PanelUi.addVerticalComponent(participant, joinButton);
		PanelUi.addVerticalComponent(this, participant);

		JPanel session = new JPanel(new BorderLayout(8, 0));
		session.setBorder(BorderFactory.createTitledBorder("Session"));
		session.add(statusLabel, BorderLayout.CENTER);
		PanelUi.addVerticalComponent(this, session);

		JPanel safetyButtons = new JPanel(new GridLayout(1, 3, 4, 0));
		safetyButtons.add(emergencyButton);
		safetyButtons.add(resumeButton);
		safetyButtons.add(endButton);
		PanelUi.addVerticalComponent(this, safetyButtons);

		createButton.addActionListener(event -> createInvitation());
		joinButton.addActionListener(event -> joinInvitation());
		emergencyButton.addActionListener(event -> sessionManager.emergencyPause());
		resumeButton.addActionListener(event -> sessionManager.resumeParticipant());
		endButton.addActionListener(event -> sessionManager.endSession());

		sessionManager.addListener(this);
		applySnapshot(sessionManager.getSnapshot());
	}

	void close()
	{
		sessionManager.removeListener(this);
	}

	@Override
	public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
	{
		SwingUtilities.invokeLater(() -> applySnapshot(snapshot));
	}

	private void createInvitation()
	{
		String relayUrl = relayUrlField.getText().trim();
		if (relayUrl.isEmpty())
		{
			showError("Enter the wss:// URL of your HapticScape relay first.");
			return;
		}
		try
		{
			configManager.setConfiguration(
				HapticScapeConfig.GROUP,
				HapticScapeConfig.REMOTE_RELAY_URL_KEY,
				relayUrl
			);
			RemoteInvitation invitation = sessionManager.startController(relayUrl);
			invitationOutput.setText(invitation.encode());
			invitationOutput.setCaretPosition(0);
		}
		catch (RuntimeException e)
		{
			showError(e.getMessage());
		}
	}

	private void joinInvitation()
	{
		try
		{
			String encoded = invitationInput.getText();
			RemoteInvitation invitation = RemoteInvitation.parse(encoded);
			int choice = JOptionPane.showConfirmDialog(
				this,
				"<html>Join Remote Control through:<br><b>"
					+ invitation.getRelayUrl()
					+ "</b><br><br>The controller will become authoritative for "
					+ "HapticScape feedback settings while the session is active.<br>"
					+ "Emergency Off and End Session always remain local.<br><br>"
					+ "The relay operator can see your network IP. HapticScape does not "
					+ "send your IP to the paired client.</html>",
				"Accept Remote Control",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			if (choice != JOptionPane.YES_OPTION)
			{
				return;
			}
			sessionManager.joinParticipant(encoded);
		}
		catch (RuntimeException e)
		{
			showError(e.getMessage());
		}
	}

	private void applySnapshot(RemoteSessionSnapshot snapshot)
	{
		statusLabel.setText(snapshot.getMessage());
		boolean local = snapshot.getState() == RemoteSessionState.LOCAL;
		boolean participant = snapshot.getRole() == RemoteRole.PARTICIPANT && !local;
		boolean emergencyPaused = snapshot.getState() == RemoteSessionState.EMERGENCY_PAUSED;

		createButton.setEnabled(local);
		joinButton.setEnabled(local);
		relayUrlField.setEnabled(local);
		invitationInput.setEnabled(local);
		emergencyButton.setEnabled(participant && !emergencyPaused);
		resumeButton.setEnabled(participant && emergencyPaused);
		endButton.setEnabled(!local);
	}

	private void showError(String message)
	{
		JOptionPane.showMessageDialog(
			this,
			message == null ? "Remote Control operation failed" : message,
			"Remote Control",
			JOptionPane.ERROR_MESSAGE
		);
	}

	private static JPanel row(String name, java.awt.Component control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.add(new JLabel(name), BorderLayout.WEST);
		row.add(control, BorderLayout.CENTER);
		return row;
	}
}
