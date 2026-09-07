package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRule;
import com.ashy0019.hapticscape.clicker.ClickerPhraseRules;
import com.ashy0019.hapticscape.host.ExternalLinkOpener;
import com.ashy0019.hapticscape.host.GlobalUiHooks;
import com.ashy0019.hapticscape.host.TextClipboard;
import com.ashy0019.hapticscape.remote.RemoteActionAcknowledgement;
import com.ashy0019.hapticscape.remote.DiscordPairingBridge;
import com.ashy0019.hapticscape.remote.DiscordJoinRequest;
import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import com.ashy0019.hapticscape.remote.RemotePairingService;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockProposal;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import com.ashy0019.hapticscape.remote.SettingsStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

final class RemoteControlPanel extends JPanel implements RemoteSessionListener
{
	private final HapticScapeSettingsSource config;
	private final RemoteSessionManager sessionManager;
	private final TextClipboard clipboard;
	private final SidebarTextLabel statusText = new SidebarTextLabel("Local control");
	private final JButton emergencyButton = new JButton("EMERGENCY OFF");
	private final JButton resumeButton = new JButton("Resume");
	private final JButton endButton = new JButton("End session");
	private final JPanel settingsLockPanel = new JPanel();
	private final SidebarTextLabel settingsLockStatusText = new SidebarTextLabel(
		"No post-session lock requested"
	);
	private final JLabel settingsLockSelectionText = new JLabel("Selected lock targets: 0");
	private final JButton armSettingsLockButton = new JButton("Generate unlock key");
	private final JButton cancelSettingsLockButton = new JButton("Cancel lock");
	private final RemotePairingPanel pairingPanel;
	private final SavedUnlockKeysPanel savedUnlockKeysPanel;
	private final RemotePermissionsPanel permissionsPanel;
	private final RemoteActionsPanel actionsPanel;
	private final RemoteLiveForgePanel liveForgePanel;
	private final SettingsLockDraft settingsLockDraft;
	private final Runnable settingsLockDraftListener;
	private int nextLayoutRow;
	private boolean wasLocal = true;

	RemoteControlPanel(
		HapticScapeSettingsSource config,
		SettingsStore settingsStore,
		ExternalLinkOpener externalLinkOpener,
		TextClipboard clipboard,
		GlobalUiHooks globalUiHooks,
		RemoteSessionManager sessionManager,
		RemotePairingService pairingService,
		DiscordPairingBridge discordPairingBridge,
		SettingsLockDraft settingsLockDraft)
	{
		this.config = config;
		this.clipboard = clipboard;
		this.sessionManager = sessionManager;
		this.settingsLockDraft = settingsLockDraft;
		this.settingsLockDraftListener = this::handleSettingsLockDraftChanged;
		this.savedUnlockKeysPanel = new SavedUnlockKeysPanel(sessionManager, clipboard);
		this.permissionsPanel = new RemotePermissionsPanel(sessionManager);
		this.actionsPanel = new RemoteActionsPanel(sessionManager);
		this.liveForgePanel = new RemoteLiveForgePanel(sessionManager, globalUiHooks);
		this.pairingPanel = new RemotePairingPanel(
			config,
			settingsStore,
			externalLinkOpener,
			clipboard,
			sessionManager,
			pairingService,
			discordPairingBridge,
			statusText::setPlainText,
			this::showError
		);
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

		addSection(pairingPanel);

		JPanel session = new JPanel(new BorderLayout(8, 0));
		session.setBorder(BorderFactory.createTitledBorder("Session"));
		session.add(statusText, BorderLayout.CENTER);
		allowHorizontalShrink(session);
		addSection(session);
		addSection(permissionsPanel);
		addSection(actionsPanel);
		addSection(liveForgePanel);

		settingsLockPanel.setLayout(new BoxLayout(settingsLockPanel, BoxLayout.Y_AXIS));
		settingsLockPanel.setBorder(
			BorderFactory.createTitledBorder("Post-session settings lock")
		);
		SidebarTextLabel lockExplanation = new SidebarTextLabel(
			"Ask the participant to keep the final feedback settings locked after "
				+ "the session. They must approve the request. HapticScape generates "
				+ "the unlock key for you. Shift-click settings in the Subject workspace "
				+ "to select individual settings, section headers, or phrase rules."
		);
		PanelUi.addVerticalComponent(settingsLockPanel, lockExplanation);
		Dimension selectionSize = new Dimension(
			180,
			settingsLockSelectionText.getPreferredSize().height
		);
		settingsLockSelectionText.setPreferredSize(selectionSize);
		settingsLockSelectionText.setMinimumSize(new Dimension(0, selectionSize.height));
		PanelUi.addVerticalComponent(settingsLockPanel, settingsLockSelectionText);
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

		emergencyButton.addActionListener(event -> sessionManager.emergencyPause());
		resumeButton.addActionListener(event -> sessionManager.resumeParticipant());
		endButton.addActionListener(event -> sessionManager.endSession());
		armSettingsLockButton.addActionListener(event -> armSettingsLock());
		cancelSettingsLockButton.addActionListener(event -> sessionManager.cancelSettingsLock());

		sessionManager.addListener(this);
		settingsLockDraft.addListener(settingsLockDraftListener);
		savedUnlockKeysPanel.refresh();
		applySnapshot(sessionManager.getSnapshot());
	}

	void close()
	{
		pairingPanel.close();
		liveForgePanel.close();
		settingsLockDraft.removeListener(settingsLockDraftListener);
		sessionManager.removeListener(this);
	}

	boolean confirmDiscordRemoteControl(DiscordJoinRequest request)
	{
		return pairingPanel.confirmDiscordRemoteControl(request);
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
			liveForgePanel.apply(sessionManager.getSnapshot(), permissions);
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
		SwingUtilities.invokeLater(() -> confirmSettingsLockProposal(proposal));
	}

	private void armSettingsLock()
	{
		Collection<SettingsLockTarget> targets = settingsLockDraft.snapshot();
		if (targets.isEmpty())
		{
			showError(
				"Shift-click one or more settings in the Subject workspace first."
			);
			return;
		}
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
		PanelUi.addVerticalComponent(
			content,
			new JLabel(targets.size() + (targets.size() == 1
				? " setting will be locked:"
				: " settings will be locked:"))
		);
		PanelUi.addVerticalComponent(content, createTargetList(targets));
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
			clipboard.copyText(keyField.getText());
			sessionManager.proposeSettingsLock(unlockKey, targets);
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

	private void confirmSettingsLockProposal(SettingsLockProposal proposal)
	{
		if (!phraseTargetsExist(proposal.getTargets()))
		{
			sessionManager.declinePendingSettingsLock();
			showError(
				"The lock request referenced a phrase rule that no longer exists. "
					+ "Ask the controller to create a new request."
			);
			return;
		}
		SidebarTextLabel explanation = new SidebarTextLabel(
			"The controller requests a persistent lock on the settings listed below. "
				+ "Their final values will stay locked after this session ends. Only the "
				+ "controller's generated key can unlock this bundle normally."
		);
		SidebarTextLabel safety = new SidebarTextLabel(
			"Emergency Off, End Session, Intiface controls, remote permissions, "
				+ "Forge, Music, and developer recovery remain available."
		);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		PanelUi.addVerticalComponent(content, explanation);
		PanelUi.addVerticalComponent(content, createTargetList(proposal.getTargets()));
		PanelUi.addVerticalComponent(content, safety);
		int choice = JOptionPane.showConfirmDialog(
			this,
			content,
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
			settingsLockDraft.clear();
		}
		wasLocal = local;

		pairingPanel.apply(snapshot);
		permissionsPanel.setVisible(local || participant);
		actionsPanel.apply(
			snapshot,
			sessionManager.getPeerPermissions(),
			sessionManager.getControllerSettingsSnapshot()
		);
		liveForgePanel.apply(snapshot, sessionManager.getPeerPermissions());
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
		if (state == RemoteLockState.ARMED && settingsLockDraft.size() > 0)
		{
			settingsLockDraft.clear();
		}
		refreshSettingsLockSelectionText();
		savedUnlockKeysPanel.refresh();
		boolean mayRequest = state == RemoteLockState.INACTIVE
			|| state == RemoteLockState.DECLINED;
		refreshArmSettingsLockButton(controllerActive, mayRequest);
		boolean mayCancel = state == RemoteLockState.AWAITING_APPROVAL
			|| state == RemoteLockState.ARMED
			|| state == RemoteLockState.DECLINED;
		cancelSettingsLockButton.setVisible(mayCancel);
		cancelSettingsLockButton.setEnabled(controllerActive && mayCancel);
		refreshSectionMinimumHeights();
		settingsLockPanel.revalidate();
		settingsLockPanel.repaint();
	}

	private void handleSettingsLockDraftChanged()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::handleSettingsLockDraftChanged);
			return;
		}
		RemoteSessionSnapshot session = sessionManager.getSnapshot();
		RemoteLockState state = sessionManager.getLockSnapshot().getState();
		boolean controllerActive = session.getRole() == RemoteRole.CONTROLLER
			&& (session.getState() == RemoteSessionState.ACTIVE
				|| session.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED);
		boolean mayRequest = state == RemoteLockState.INACTIVE
			|| state == RemoteLockState.DECLINED;
		refreshSettingsLockSelectionText();
		refreshArmSettingsLockButton(controllerActive, mayRequest);
		settingsLockSelectionText.repaint();
		armSettingsLockButton.repaint();
	}

	private void refreshSettingsLockSelectionText()
	{
		settingsLockSelectionText.setText(
			"Selected lock targets: " + settingsLockDraft.size()
		);
	}

	private void refreshArmSettingsLockButton(
		boolean controllerActive,
		boolean mayRequest)
	{
		armSettingsLockButton.setEnabled(
			controllerActive
				&& mayRequest
				&& settingsLockDraft.size() > 0
				&& sessionManager.isSavedUnlockKeyVaultAvailable()
		);
		armSettingsLockButton.setToolTipText(
			!sessionManager.isSavedUnlockKeyVaultAvailable()
				? sessionManager.getSavedUnlockKeyVaultMessage()
				: settingsLockDraft.size() == 0
					? "Shift-click settings in the Subject workspace first"
					: null
		);
	}

	private JScrollPane createTargetList(
		Collection<SettingsLockTarget> targets)
	{
		JTextArea list = new JTextArea(formatTargets(targets), 9, 28);
		list.setEditable(false);
		list.setLineWrap(false);
		list.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(300, 150));
		return scroll;
	}

	private String formatTargets(
		Collection<SettingsLockTarget> targets)
	{
		List<SettingsLockTarget> ordered = new ArrayList<>(targets);
		ordered.sort(Comparator.naturalOrder());
		StringBuilder text = new StringBuilder();
		String group = null;
		for (SettingsLockTarget target : ordered)
		{
			if (!target.getGroup().equals(group))
			{
				if (text.length() > 0)
				{
					text.append('\n');
				}
				group = target.getGroup();
				text.append(group).append(':').append('\n');
			}
			text.append("  • ").append(displayName(target)).append('\n');
		}
		return text.toString();
	}

	private String displayName(SettingsLockTarget target)
	{
		if (!SettingsLockCatalog.isPhraseRule(target))
		{
			return target.getDisplayName();
		}
		String id = SettingsLockCatalog.phraseRuleId(target);
		for (ClickerPhraseRule rule : visiblePhraseRules().getRules())
		{
			if (id.equals(rule.getId()))
			{
				return "Phrase rule: " + rule.toString();
			}
		}
		return target.getDisplayName() + " (no longer present)";
	}

	private boolean phraseTargetsExist(Collection<SettingsLockTarget> targets)
	{
		ClickerPhraseRules rules = visiblePhraseRules();
		for (SettingsLockTarget target : targets)
		{
			if (!SettingsLockCatalog.isPhraseRule(target))
			{
				continue;
			}
			String id = SettingsLockCatalog.phraseRuleId(target);
			boolean found = false;
			for (ClickerPhraseRule rule : rules.getRules())
			{
				if (id.equals(rule.getId()))
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				return false;
			}
		}
		return true;
	}

	private ClickerPhraseRules visiblePhraseRules()
	{
		RemoteSettingsSnapshot remote = sessionManager.getControllerSettingsSnapshot();
		if (sessionManager.getSnapshot().getRole() == RemoteRole.CONTROLLER && remote != null)
		{
			return remote.getClickerPhraseRules();
		}
		return ClickerPhraseRules.fromConfigValue(config.clickerPhraseRules());
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
