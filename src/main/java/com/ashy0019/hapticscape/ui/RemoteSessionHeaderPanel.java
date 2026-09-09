package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Persistent role, connection-state, and local safety header for Remote Play. */
final class RemoteSessionHeaderPanel extends JPanel
{
	private static final int STACK_BREAKPOINT = 680;

	private final JLabel roleLabel = new JLabel();
	private final WrappedTextLabel statusLabel = new WrappedTextLabel("");
	private final JPanel details = new JPanel(new GridBagLayout());
	private final JPanel actions = new JPanel(new GridBagLayout());
	private int layoutMode = -1;

	RemoteSessionHeaderPanel(
		JButton emergencyButton,
		JButton resumeButton,
		JButton endButton)
	{
		setName("remoteSessionHeader");
		setLayout(new GridBagLayout());
		setBorder(BorderFactory.createTitledBorder("Session"));

		roleLabel.setName("remoteSessionRole");
		roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD));
		statusLabel.setName("remoteSessionStatus");
		GridBagConstraints roleConstraints = new GridBagConstraints();
		roleConstraints.gridx = 0;
		roleConstraints.gridy = 0;
		roleConstraints.weightx = 1.0;
		roleConstraints.fill = GridBagConstraints.HORIZONTAL;
		roleConstraints.anchor = GridBagConstraints.WEST;
		details.add(roleLabel, roleConstraints);
		GridBagConstraints statusConstraints = new GridBagConstraints();
		statusConstraints.gridx = 0;
		statusConstraints.gridy = 1;
		statusConstraints.weightx = 1.0;
		statusConstraints.fill = GridBagConstraints.HORIZONTAL;
		statusConstraints.anchor = GridBagConstraints.WEST;
		statusConstraints.insets = new Insets(2, 0, 0, 0);
		details.add(statusLabel, statusConstraints);

		addAction(emergencyButton, 0);
		addAction(resumeButton, 1);
		addAction(endButton, 2);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				reflow();
			}
		});
		reflow();
	}

	void apply(RemoteSessionSnapshot snapshot)
	{
		roleLabel.setText(titleFor(snapshot));
		statusLabel.setPlainText(snapshot.getMessage());
		statusLabel.setToolTipText(snapshot.getMessage());
		actions.revalidate();
		revalidate();
		repaint();
	}

	private void addAction(Component component, int column)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = column;
		constraints.gridy = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, column == 0 ? 0 : 4, 0, 0);
		actions.add(component, constraints);
	}

	private void reflow()
	{
		int nextMode = layoutModeForWidth(getWidth());
		if (nextMode == layoutMode)
		{
			return;
		}
		layoutMode = nextMode;
		removeAll();

		GridBagConstraints detailsConstraints = new GridBagConstraints();
		detailsConstraints.gridx = 0;
		detailsConstraints.gridy = 0;
		detailsConstraints.weightx = 1.0;
		detailsConstraints.fill = GridBagConstraints.HORIZONTAL;
		detailsConstraints.anchor = GridBagConstraints.NORTHWEST;
		if (layoutMode == 1)
		{
			detailsConstraints.gridwidth = 2;
		}
		add(details, detailsConstraints);

		GridBagConstraints actionConstraints = new GridBagConstraints();
		actionConstraints.gridx = layoutMode == 1 ? 0 : 1;
		actionConstraints.gridy = layoutMode == 1 ? 1 : 0;
		actionConstraints.weightx = layoutMode == 1 ? 1.0 : 0.0;
		actionConstraints.fill = GridBagConstraints.HORIZONTAL;
		actionConstraints.anchor = layoutMode == 1
			? GridBagConstraints.WEST
			: GridBagConstraints.NORTHEAST;
		actionConstraints.insets = layoutMode == 1
			? new Insets(7, 0, 0, 0)
			: new Insets(0, 8, 0, 0);
		add(actions, actionConstraints);
		revalidate();
		repaint();
	}

	static int layoutModeForWidth(int width)
	{
		return width >= STACK_BREAKPOINT ? 2 : 1;
	}

	static String titleFor(RemoteSessionSnapshot snapshot)
	{
		RemoteSessionState state = snapshot.getState();
		if (state == RemoteSessionState.DISCONNECTED)
		{
			return "Session disconnected";
		}
		if (state == RemoteSessionState.CONNECTING
			|| state == RemoteSessionState.WAITING_FOR_SETTINGS)
		{
			return "Securing session...";
		}
		if (snapshot.getRole() == RemoteRole.PARTICIPANT)
		{
			return state == RemoteSessionState.EMERGENCY_PAUSED
				? "Remote control paused"
				: "You are being controlled";
		}
		if (snapshot.getRole() == RemoteRole.CONTROLLER)
		{
			return state == RemoteSessionState.PEER_EMERGENCY_PAUSED
				? "Partner paused remote control"
				: "Controlling partner";
		}
		return "Remote Play";
	}
}
