package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/** One Forge workspace with persistent composition and ephemeral live-control modes. */
final class ForgeWorkspacePanel extends JPanel implements RemoteSessionListener
{
	private static final String COMPOSE = "compose";
	private static final String LIVE = "live";

	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JToggleButton composeButton = new JToggleButton("Compose");
	private final JToggleButton liveButton = new JToggleButton("Live");
	private final JLabel liveState = new JLabel();
	private final JPanel modeBar = new JPanel(new BorderLayout(8, 2));
	private final JPanel modeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final RemoteLiveForgePanel livePanel;
	private final RemoteSessionManager sessionManager;
	private int modeBarLayout = -1;

	ForgeWorkspacePanel(
		CustomPatternsPanel composePanel,
		RemoteLiveForgePanel livePanel,
		RemoteSessionManager sessionManager)
	{
		super(new BorderLayout(0, 6));
		this.livePanel = Objects.requireNonNull(livePanel, "livePanel");
		this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
		Objects.requireNonNull(composePanel, "composePanel");
		setName("forgeWorkspace");

		ButtonGroup modes = new ButtonGroup();
		modes.add(composeButton);
		modes.add(liveButton);
		composeButton.setName("forgeComposeMode");
		liveButton.setName("forgeLiveMode");
		composeButton.setSelected(true);
		PanelUi.configureModeTab(composeButton);
		PanelUi.configureModeTab(liveButton);
		composeButton.setToolTipText("Build and save reusable patterns");
		liveButton.setToolTipText("Control a participant continuously during Remote Play");
		modeBar.setBackground(HapticScapeTheme.SURFACE);
		modeBar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, HapticScapeTheme.BORDER),
			BorderFactory.createEmptyBorder(4, 5, 4, 5)
		));
		modeButtons.setOpaque(false);
		liveState.setForeground(HapticScapeTheme.MUTED_TEXT);
		JLabel forgeLabel = new JLabel("FORGE");
		forgeLabel.setForeground(HapticScapeTheme.MUTED_TEXT);
		forgeLabel.setFont(forgeLabel.getFont().deriveFont(
			Math.max(9.0f, forgeLabel.getFont().getSize2D() - 1.0f)
		));
		modeButtons.add(forgeLabel);
		modeButtons.add(composeButton);
		modeButtons.add(liveButton);
		add(modeBar, BorderLayout.NORTH);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflowModeBar();
			}
		});
		reflowModeBar();

		content.add(composePanel, COMPOSE);
		content.add(livePanel, LIVE);
		add(content, BorderLayout.CENTER);
		composeButton.addActionListener(event -> showCompose());
		liveButton.addActionListener(event -> showLive());
		livePanel.setWorkspaceActive(false);
		sessionManager.addListener(this);
		apply(sessionManager.getSnapshot(), sessionManager.getPeerPermissions());
	}

	void showCompose()
	{
		livePanel.setWorkspaceActive(false);
		composeButton.setSelected(true);
		cards.show(content, COMPOSE);
	}

	void showLive()
	{
		if (!liveButton.isEnabled())
		{
			showCompose();
			return;
		}
		liveButton.setSelected(true);
		cards.show(content, LIVE);
		livePanel.setWorkspaceActive(true);
	}

	void leaveWorkspace()
	{
		livePanel.setWorkspaceActive(false);
	}

	void enterWorkspace()
	{
		if (liveButton.isSelected() && liveButton.isEnabled())
		{
			livePanel.setWorkspaceActive(true);
		}
	}

	void close()
	{
		leaveWorkspace();
		sessionManager.removeListener(this);
		livePanel.close();
	}

	@Override
	public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
	{
		SwingUtilities.invokeLater(() -> apply(snapshot, sessionManager.getPeerPermissions()));
	}

	@Override
	public void onRemotePermissionsChanged(RemotePermissions permissions)
	{
		SwingUtilities.invokeLater(() -> apply(sessionManager.getSnapshot(), permissions));
	}

	private void apply(RemoteSessionSnapshot snapshot, RemotePermissions permissions)
	{
		livePanel.apply(snapshot, permissions);
		boolean available = snapshot.getRole() == com.ashy0019.hapticscape.remote.RemoteRole.CONTROLLER
			&& (snapshot.getState() == com.ashy0019.hapticscape.remote.RemoteSessionState.ACTIVE
				|| snapshot.getState() == com.ashy0019.hapticscape.remote.RemoteSessionState.PEER_EMERGENCY_PAUSED);
		liveButton.setEnabled(available);
		liveState.setText(available
			? "Live limit: " + permissions.getMaximumIntensityPercent() + "%"
			: "Live requires an active controller session");
		if (!available && liveButton.isSelected())
		{
			showCompose();
		}
	}

	private void reflowModeBar()
	{
		boolean compact = getWidth() > 0 && getWidth() < 620;
		int desired = compact ? 1 : 2;
		if (desired == modeBarLayout)
		{
			return;
		}
		modeBarLayout = desired;
		modeBar.removeAll();
		modeBar.add(modeButtons, compact ? BorderLayout.NORTH : BorderLayout.WEST);
		modeBar.add(liveState, compact ? BorderLayout.SOUTH : BorderLayout.EAST);
		modeBar.revalidate();
		modeBar.repaint();
	}
}
