package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteActivityEvent;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Bounded, read-only pulse of participant gameplay for the controller. */
final class RemoteActivityFeedPanel extends JPanel
{
	private static final int MAXIMUM_ENTRIES = 50;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
		.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private final DefaultListModel<RemoteActivityEvent> model = new DefaultListModel<>();
	private final JList<RemoteActivityEvent> list = new JList<>(model);
	private final JLabel emptyState = new JLabel("Waiting for shared activity...");
	private boolean sharingAllowed;

	RemoteActivityFeedPanel()
	{
		setName("remoteActivityFeed");
		setLayout(new BorderLayout(0, 5));
		setBorder(PanelUi.createSectionBorder("Subject activity"));

		emptyState.setName("remoteActivityFeedState");
		add(emptyState, BorderLayout.NORTH);

		list.setName("remoteActivityFeedList");
		list.setVisibleRowCount(6);
		list.setFocusable(false);
		list.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(
				JList<?> renderedList,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
			{
				super.getListCellRendererComponent(
					renderedList,
					value,
					index,
					isSelected,
					cellHasFocus
				);
				if (value instanceof RemoteActivityEvent)
				{
					RemoteActivityEvent event = (RemoteActivityEvent) value;
					String detail = event.getDetail().isEmpty() ? "" : "   " + event.getDetail();
					setText(
						TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimestampMillis()))
							+ "   " + event.getLabel() + detail
					);
				}
				return this;
			}
		});

		JScrollPane scroll = new JScrollPane(list);
		scroll.setName("remoteActivityFeedScroll");
		PanelUi.setFlexibleWidthHeightHint(scroll, 132, 96);
		add(scroll, BorderLayout.CENTER);
		setPreferredSize(new Dimension(0, 184));
		updateState();
	}

	void apply(RemoteSessionSnapshot snapshot, RemotePermissions permissions)
	{
		boolean controllerSession = snapshot.getRole() == RemoteRole.CONTROLLER
			&& (snapshot.getState() == RemoteSessionState.ACTIVE
				|| snapshot.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED);
		boolean nextAllowed = controllerSession && permissions.isActivitySharingAllowed();
		if (!controllerSession || (sharingAllowed && !nextAllowed))
		{
			clear();
		}
		sharingAllowed = nextAllowed;
		updateState();
	}

	void addActivity(RemoteActivityEvent event)
	{
		if (!sharingAllowed || event == null)
		{
			return;
		}
		boolean followTail = model.isEmpty()
			|| list.getLastVisibleIndex() >= model.size() - 1;
		model.addElement(event);
		while (model.size() > MAXIMUM_ENTRIES)
		{
			model.remove(0);
		}
		if (followTail && !model.isEmpty())
		{
			list.ensureIndexIsVisible(model.size() - 1);
		}
		updateState();
	}

	void clear()
	{
		model.clear();
		updateState();
	}

	int entryCount()
	{
		return model.size();
	}

	private void updateState()
	{
		if (!sharingAllowed)
		{
			emptyState.setText("Activity sharing disabled by participant.");
			emptyState.setVisible(true);
		}
		else if (model.isEmpty())
		{
			emptyState.setText("Waiting for shared activity...");
			emptyState.setVisible(true);
		}
		else
		{
			emptyState.setVisible(false);
		}
	}
}
