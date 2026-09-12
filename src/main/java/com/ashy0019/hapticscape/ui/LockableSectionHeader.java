package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Fixed-layout selector for a whole settings section. */
final class LockableSectionHeader extends JPanel
{
	private static final Color LOCK_COLOR = new Color(255, 174, 0);
	private static final Color MUTED_LOCK_COLOR = new Color(155, 155, 155);
	private static final Color UNLOCK_COLOR = new Color(125, 125, 125);

	private final JLabel titleLabel;
	private final JLabel lockLabel = new JLabel(new SectionLockIcon());
	private final Supplier<SettingsLockTarget> targetSupplier;
	private final SettingsLockDraft draft;
	private final SettingsLockService lockService;
	private final Supplier<RemoteLockSnapshot> remoteLockSupplier;
	private final BooleanSupplier editingRemoteSubject;
	private final BooleanSupplier selectionEnabled;

	LockableSectionHeader(
		String title,
		Supplier<SettingsLockTarget> targetSupplier,
		SettingsLockDraft draft,
		SettingsLockService lockService,
		Supplier<RemoteLockSnapshot> remoteLockSupplier,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier selectionEnabled)
	{
		super(new BorderLayout(6, 0));
		this.targetSupplier = Objects.requireNonNull(targetSupplier, "targetSupplier");
		this.draft = Objects.requireNonNull(draft, "draft");
		this.lockService = Objects.requireNonNull(lockService, "lockService");
		this.remoteLockSupplier = Objects.requireNonNull(
			remoteLockSupplier,
			"remoteLockSupplier"
		);
		this.editingRemoteSubject = Objects.requireNonNull(
			editingRemoteSubject,
			"editingRemoteSubject"
		);
		this.selectionEnabled = Objects.requireNonNull(selectionEnabled, "selectionEnabled");

		titleLabel = new JLabel(Objects.requireNonNull(title, "title"));
		setOpaque(false);
		titleLabel.setOpaque(false);
		lockLabel.setOpaque(false);
		Dimension markerSize = new Dimension(15, 15);
		lockLabel.setPreferredSize(markerSize);
		lockLabel.setMinimumSize(markerSize);
		lockLabel.setMaximumSize(markerSize);
		add(titleLabel, BorderLayout.CENTER);
		add(lockLabel, BorderLayout.EAST);

		MouseAdapter selector = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				handleClick(event);
			}
		};
		addMouseListener(selector);
		titleLabel.addMouseListener(selector);
		lockLabel.addMouseListener(selector);
		draft.addListener(this::refresh);
		refresh();
	}

	boolean isEditLocked()
	{
		return !editingRemoteSubject.getAsBoolean() && lockService.isLocked(target());
	}

	void refresh()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::refresh);
			return;
		}
		String tooltip;
		String targetName = target().getDisplayName();
		switch (state())
		{
			case PROPOSED:
				tooltip = targetName + " are selected for post-session locking. "
					+ "Shift-click again to remove this selection.";
				break;
			case ARMED:
				tooltip = "A post-session lock is armed for " + targetName + ".";
				break;
			case PERSISTENT:
				tooltip = targetName + " are locked after a remote session.";
				break;
			default:
				tooltip = selectionEnabled.getAsBoolean()
					? "Shift-click this row or the open padlock to select " + targetName
						+ " for post-session locking."
					: "This section can be selected for locking from an active controller Subject workspace.";
		}
		setToolTipText(tooltip);
		titleLabel.setToolTipText(tooltip);
		lockLabel.setToolTipText(tooltip);
		repaint();
	}

	private void handleClick(MouseEvent event)
	{
		if ((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0
			|| !editingRemoteSubject.getAsBoolean()
			|| !selectionEnabled.getAsBoolean())
		{
			return;
		}
		draft.toggle(target());
		refresh();
	}

	private State state()
	{
		SettingsLockTarget target = target();
		if (editingRemoteSubject.getAsBoolean())
		{
			RemoteLockSnapshot remote = remoteLockSupplier.get();
			if (remote.getTargets().contains(target)
				&& remote.getState() == RemoteLockState.ARMED)
			{
				return State.ARMED;
			}
			if ((remote.getState() != RemoteLockState.INACTIVE
				&& remote.getTargets().contains(target))
				|| draft.contains(target))
			{
				return State.PROPOSED;
			}
			return State.NONE;
		}
		return lockService.isLocked(target) ? State.PERSISTENT : State.NONE;
	}

	private SettingsLockTarget target()
	{
		return Objects.requireNonNull(targetSupplier.get(), "target");
	}

	private enum State
	{
		NONE,
		PROPOSED,
		ARMED,
		PERSISTENT
	}

	private final class SectionLockIcon implements Icon
	{
		@Override
		public int getIconWidth()
		{
			return 13;
		}

		@Override
		public int getIconHeight()
		{
			return 13;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			State state = state();
			if (state == State.NONE)
			{
				paintOpenLock(graphics, x, y);
				return;
			}

			graphics.setColor(state == State.PERSISTENT
				? MUTED_LOCK_COLOR
				: LOCK_COLOR);
			paintClosedShackle(graphics, x, y);
			if (state == State.ARMED || state == State.PERSISTENT)
			{
				graphics.fillRect(x + 2, y + 6, 9, 7);
			}
			else
			{
				graphics.drawRect(x + 2, y + 6, 8, 6);
			}
		}

		private void paintOpenLock(Graphics graphics, int x, int y)
		{
			graphics.setColor(UNLOCK_COLOR);
			graphics.drawArc(x + 4, y + 1, 6, 7, 0, 180);
			graphics.drawLine(x + 10, y + 4, x + 10, y + 6);
			graphics.drawRect(x + 2, y + 6, 8, 6);
		}

		private void paintClosedShackle(Graphics graphics, int x, int y)
		{
			graphics.drawArc(x + 3, y + 1, 6, 7, 0, 180);
			graphics.drawLine(x + 3, y + 4, x + 3, y + 6);
			graphics.drawLine(x + 9, y + 4, x + 9, y + 6);
		}
	}
}
