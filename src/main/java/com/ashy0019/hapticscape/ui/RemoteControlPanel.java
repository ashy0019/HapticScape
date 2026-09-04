package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.remote.RemoteActionAcknowledgement;
import com.ashy0019.hapticscape.remote.RemoteInvitation;
import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import com.ashy0019.hapticscape.remote.SettingsLockProposal;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
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
	private final SidebarTextLabel statusText = new SidebarTextLabel("Local control");
	private final JTextField relayUrlField = new JTextField();
	private final JTextArea invitationOutput = new JTextArea(4, 24);
	private final JTextArea invitationInput = new JTextArea(4, 24);
	private final JButton createButton = new JButton("Create invitation");
	private final JButton copyButton = new JButton("Copy");
	private final JButton pasteButton = new JButton("Paste");
	private final JButton joinButton = new JButton("Join invitation");
	private final JButton emergencyButton = new JButton("EMERGENCY OFF");
	private final JButton resumeButton = new JButton("Resume");
	private final JButton endButton = new JButton("End session");
	private final JPanel settingsLockPanel = new JPanel();
	private final SidebarTextLabel settingsLockStatusText = new SidebarTextLabel(
		"No post-session lock requested"
	);
	private final JButton armSettingsLockButton = new JButton("Generate unlock key");
	private final JButton cancelSettingsLockButton = new JButton("Cancel lock");
	private final JPanel controllerPanel = new JPanel();
	private final JPanel participantPanel = new JPanel();
	private final SavedUnlockKeysPanel savedUnlockKeysPanel;
	private final RemotePermissionsPanel permissionsPanel;
	private final RemoteActionsPanel actionsPanel;
	private int nextLayoutRow;
	private boolean wasLocal = true;

	RemoteControlPanel(
		HapticScapeConfig config,
		ConfigManager configManager,
		RemoteSessionManager sessionManager)
	{
		this.configManager = configManager;
		this.sessionManager = sessionManager;
		this.savedUnlockKeysPanel = new SavedUnlockKeysPanel(sessionManager);
		this.permissionsPanel = new RemotePermissionsPanel(sessionManager);
		this.actionsPanel = new RemoteActionsPanel(sessionManager);
		setLayout(new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));

		SidebarTextLabel privacy = new SidebarTextLabel(
			"Settings are end-to-end encrypted. Peers do not connect directly, "
				+ "but the relay operator can see each client's IP."
		);
		privacy.setBorder(BorderFactory.createEmptyBorder(2, 2, 6, 2));
		privacy.setToolTipText(
			"Settings are encrypted before relay transport. If your partner operates the relay, they may be able to see connection metadata such as your IP address."
		);
		addSection(privacy);

		relayUrlField.setText(config.remoteRelayUrl());
		addSection(row("Relay", relayUrlField));

		controllerPanel.setLayout(new BoxLayout(controllerPanel, BoxLayout.Y_AXIS));
		controllerPanel.setBorder(BorderFactory.createTitledBorder("Control a partner"));
		JPanel createRow = new JPanel(new GridLayout(1, 2, 4, 0));
		allowHorizontalShrink(createRow);
		configureCompactButton(createButton);
		configureCompactButton(copyButton);
		createRow.add(createButton);
		createRow.add(copyButton);
		PanelUi.addVerticalComponent(controllerPanel, createRow);
		invitationOutput.setEditable(false);
		invitationOutput.setLineWrap(true);
		invitationOutput.setWrapStyleWord(true);
		invitationOutput.setToolTipText(
			"Share this invitation privately with the participant. It contains the session encryption key."
		);
		JScrollPane invitationOutputScroll = new JScrollPane(invitationOutput);
		allowHorizontalShrink(invitationOutputScroll);
		PanelUi.addVerticalComponent(controllerPanel, invitationOutputScroll);
		allowHorizontalShrink(controllerPanel);
		addSection(controllerPanel);

		participantPanel.setLayout(new BoxLayout(participantPanel, BoxLayout.Y_AXIS));
		participantPanel.setBorder(BorderFactory.createTitledBorder("Let a partner control you"));
		invitationInput.setLineWrap(true);
		invitationInput.setWrapStyleWord(true);
		JScrollPane invitationInputScroll = new JScrollPane(invitationInput);
		allowHorizontalShrink(invitationInputScroll);
		PanelUi.addVerticalComponent(participantPanel, invitationInputScroll);
		JPanel joinRow = new JPanel(new GridLayout(1, 2, 4, 0));
		allowHorizontalShrink(joinRow);
		configureCompactButton(pasteButton);
		configureCompactButton(joinButton);
		joinRow.add(pasteButton);
		joinRow.add(joinButton);
		PanelUi.addVerticalComponent(participantPanel, joinRow);
		allowHorizontalShrink(participantPanel);
		addSection(participantPanel);

		JPanel session = new JPanel(new BorderLayout(8, 0));
		session.setBorder(BorderFactory.createTitledBorder("Session"));
		session.add(statusText, BorderLayout.CENTER);
		allowHorizontalShrink(session);
		addSection(session);
		addSection(permissionsPanel);
		addSection(actionsPanel);

		settingsLockPanel.setLayout(new BoxLayout(settingsLockPanel, BoxLayout.Y_AXIS));
		settingsLockPanel.setBorder(
			BorderFactory.createTitledBorder("Post-session settings lock")
		);
		SidebarTextLabel lockExplanation = new SidebarTextLabel(
			"Ask the participant to keep the final feedback settings locked after "
				+ "the session. They must approve the request. HapticScape generates "
				+ "the unlock key for you."
		);
		PanelUi.addVerticalComponent(settingsLockPanel, lockExplanation);
		JPanel settingsLockButtons = new JPanel(new GridLayout(0, 1, 0, 4));
		allowHorizontalShrink(settingsLockButtons);
		configureCompactButton(armSettingsLockButton);
		configureCompactButton(cancelSettingsLockButton);
		settingsLockButtons.add(armSettingsLockButton);
		settingsLockButtons.add(cancelSettingsLockButton);
		PanelUi.addVerticalComponent(settingsLockPanel, settingsLockButtons);
		PanelUi.addVerticalComponent(settingsLockPanel, settingsLockStatusText);
		allowHorizontalShrink(settingsLockPanel);
		addSection(settingsLockPanel);

		addSection(savedUnlockKeysPanel);

		JPanel safetyButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		safetyButtons.add(emergencyButton);
		safetyButtons.add(resumeButton);
		safetyButtons.add(endButton);
		addSection(safetyButtons);

		createButton.addActionListener(event -> createInvitation());
		copyButton.addActionListener(event -> copyInvitation());
		pasteButton.addActionListener(event -> pasteInvitation());
		joinButton.addActionListener(event -> joinInvitation());
		emergencyButton.addActionListener(event -> sessionManager.emergencyPause());
		resumeButton.addActionListener(event -> sessionManager.resumeParticipant());
		endButton.addActionListener(event -> sessionManager.endSession());
		armSettingsLockButton.addActionListener(event -> armSettingsLock());
		cancelSettingsLockButton.addActionListener(event -> sessionManager.cancelSettingsLock());

		sessionManager.addListener(this);
		savedUnlockKeysPanel.refresh();
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

	@Override
	public void onRemoteLockChanged(RemoteLockSnapshot snapshot)
	{
		SwingUtilities.invokeLater(() -> applyLockSnapshot(snapshot));
	}

	@Override
	public void onRemotePermissionsChanged(RemotePermissions permissions)
	{
		SwingUtilities.invokeLater(() ->
		{
			permissionsPanel.apply(permissions);
			actionsPanel.apply(
				sessionManager.getSnapshot(),
				sessionManager.getPeerPermissions(),
				sessionManager.getControllerSettingsSnapshot()
			);
		});
	}

	@Override
	public void onRemoteSettingsChanged(RemoteSettingsSnapshot settings)
	{
		SwingUtilities.invokeLater(() -> actionsPanel.apply(
			sessionManager.getSnapshot(),
			sessionManager.getPeerPermissions(),
			settings
		));
	}

	@Override
	public void onRemoteActionAcknowledged(RemoteActionAcknowledgement acknowledgement)
	{
		SwingUtilities.invokeLater(() -> actionsPanel.showAcknowledgement(acknowledgement));
	}

	@Override
	public void onRemoteLockProposal(SettingsLockProposal proposal)
	{
		SwingUtilities.invokeLater(this::confirmSettingsLockProposal);
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

	private void armSettingsLock()
	{
		if (!sessionManager.isSavedUnlockKeyVaultAvailable())
		{
			showError(sessionManager.getSavedUnlockKeyVaultMessage());
			return;
		}
		char[] unlockKey = sessionManager.generateSettingsLockKey();
		JTextField keyField = new JTextField(new String(unlockKey));
		keyField.setEditable(false);
		keyField.setHorizontalAlignment(JTextField.CENTER);
		SidebarTextLabel explanation = new SidebarTextLabel(
			"HapticScape will save this unlock key only if the participant accepts "
				+ "the lock. The saved copy is encrypted by Windows for your account."
		);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(content, explanation);
		PanelUi.addVerticalComponent(content, keyField);
		try
		{
			Object[] options = {"Copy key & request", "Cancel"};
			int choice = JOptionPane.showOptionDialog(
				this,
				content,
				"Generated settings unlock key",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				options,
				options[0]
			);
			if (choice != JOptionPane.YES_OPTION)
			{
				return;
			}
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(keyField.getText()),
				null
			);
			sessionManager.proposeSettingsLock(unlockKey);
		}
		catch (RuntimeException e)
		{
			showError(e.getMessage());
		}
		finally
		{
			Arrays.fill(unlockKey, '\0');
			keyField.setText("");
		}
	}

	private void confirmSettingsLockProposal()
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"<html>The controller requests a persistent settings lock.<br><br>"
				+ "If accepted, the final feedback settings will stay locked after "
				+ "this session ends.<br>Only the controller's generated key can unlock "
				+ "them normally.<br><br>Emergency Off, End Session, Intiface controls, "
				+ "and developer recovery remain available.</html>",
			"Accept post-session settings lock?",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (choice == JOptionPane.YES_OPTION)
		{
			sessionManager.acceptPendingSettingsLock();
		}
		else
		{
			sessionManager.declinePendingSettingsLock();
		}
	}

	private void applySnapshot(RemoteSessionSnapshot snapshot)
	{
		statusText.setPlainText(snapshot.getMessage());
		boolean local = snapshot.getState() == RemoteSessionState.LOCAL;
		boolean controller = snapshot.getRole() == RemoteRole.CONTROLLER && !local;
		boolean participant = snapshot.getRole() == RemoteRole.PARTICIPANT && !local;
		boolean emergencyPaused = snapshot.getState() == RemoteSessionState.EMERGENCY_PAUSED;
		if (local && !wasLocal)
		{
			invitationOutput.setText("");
			invitationInput.setText("");
		}
		wasLocal = local;

		createButton.setEnabled(local);
		joinButton.setEnabled(local);
		relayUrlField.setEnabled(local);
		invitationInput.setEnabled(local);
		copyButton.setEnabled(controller && !invitationOutput.getText().trim().isEmpty());
		pasteButton.setEnabled(local);
		controllerPanel.setVisible(local || controller
			&& (snapshot.getState() == RemoteSessionState.CONNECTING
				|| snapshot.getState() == RemoteSessionState.WAITING_FOR_PEER));
		participantPanel.setVisible(local);
		permissionsPanel.setVisible(local || participant);
		actionsPanel.apply(
			snapshot,
			sessionManager.getPeerPermissions(),
			sessionManager.getControllerSettingsSnapshot()
		);
		savedUnlockKeysPanel.setVisible(!participant);
		emergencyButton.setEnabled(participant && !emergencyPaused);
		resumeButton.setEnabled(participant && emergencyPaused);
		emergencyButton.setVisible(participant && !emergencyPaused);
		resumeButton.setVisible(participant && emergencyPaused);
		endButton.setEnabled(!local);
		endButton.setVisible(!local);
		applyLockSnapshot(sessionManager.getLockSnapshot());
		refreshSectionMinimumHeights();
		revalidate();
		repaint();
	}

	private void applyLockSnapshot(RemoteLockSnapshot snapshot)
	{
		RemoteSessionSnapshot session = sessionManager.getSnapshot();
		boolean controllerActive = session.getRole() == RemoteRole.CONTROLLER
			&& (session.getState() == RemoteSessionState.ACTIVE
				|| session.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED);
		RemoteLockState state = snapshot.getState();
		settingsLockPanel.setVisible(controllerActive);
		settingsLockStatusText.setPlainText(snapshot.getMessage());
		savedUnlockKeysPanel.refresh();
		boolean mayRequest = state == RemoteLockState.INACTIVE
			|| state == RemoteLockState.DECLINED;
		armSettingsLockButton.setEnabled(
			controllerActive
				&& mayRequest
				&& sessionManager.isSavedUnlockKeyVaultAvailable()
		);
		armSettingsLockButton.setToolTipText(
			sessionManager.isSavedUnlockKeyVaultAvailable()
				? null
				: sessionManager.getSavedUnlockKeyVaultMessage()
		);
		boolean mayCancel = state == RemoteLockState.AWAITING_APPROVAL
			|| state == RemoteLockState.ARMED
			|| state == RemoteLockState.DECLINED;
		cancelSettingsLockButton.setVisible(mayCancel);
		cancelSettingsLockButton.setEnabled(controllerActive && mayCancel);
		refreshSectionMinimumHeights();
		settingsLockPanel.revalidate();
		settingsLockPanel.repaint();
	}

	private void copyInvitation()
	{
		String invitation = invitationOutput.getText().trim();
		if (invitation.isEmpty())
		{
			return;
		}
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(invitation),
				null
			);
			statusText.setPlainText("Invitation copied");
		}
		catch (RuntimeException e)
		{
			showError("Could not copy the invitation to the clipboard.");
		}
	}

	private void pasteInvitation()
	{
		try
		{
			Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
			if (value instanceof String)
			{
				invitationInput.setText(((String) value).trim());
				invitationInput.setCaretPosition(0);
			}
		}
		catch (Exception e)
		{
			showError("Could not paste an invitation from the clipboard.");
		}
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
		allowHorizontalShrink(row);
		if (control instanceof JComponent)
		{
			allowHorizontalShrink((JComponent) control);
		}
		return row;
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

	private void addSection(JComponent component)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = nextLayoutRow++;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = new java.awt.Insets(0, 0, 6, 0);
		allowHorizontalShrink(component);
		add(component, constraints);
	}

	private void refreshSectionMinimumHeights()
	{
		for (java.awt.Component component : getComponents())
		{
			if (component instanceof JComponent)
			{
				allowHorizontalShrink((JComponent) component);
			}
		}
	}
}
