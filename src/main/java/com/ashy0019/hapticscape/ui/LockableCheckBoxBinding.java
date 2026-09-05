package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemoteLockSnapshot;
import com.ashy0019.hapticscape.remote.RemoteLockState;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Adds the controller Shift-click gesture and compact lock marker to a checkbox. */
final class LockableCheckBoxBinding
{
	private static final Color LOCK_COLOR = new Color(255, 174, 0);
	private static final Color MUTED_LOCK_COLOR = new Color(170, 170, 170);

	private final JCheckBox checkBox;
	private final Supplier<SettingsLockTarget> targetSupplier;
	private final SettingsLockDraft draft;
	private final SettingsLockService lockService;
	private final Supplier<RemoteLockSnapshot> remoteLockSupplier;
	private final BooleanSupplier editingRemoteSubject;
	private final BooleanSupplier selectionEnabled;
	private final BooleanSupplier authoritativeValue;
	private final String originalToolTip;

	LockableCheckBoxBinding(
		JCheckBox checkBox,
		SettingsLockTarget target,
		SettingsLockDraft draft,
		SettingsLockService lockService,
		Supplier<RemoteLockSnapshot> remoteLockSupplier,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier selectionEnabled,
		BooleanSupplier authoritativeValue)
	{
		this(
			checkBox,
			() -> target,
			draft,
			lockService,
			remoteLockSupplier,
			editingRemoteSubject,
			selectionEnabled,
			authoritativeValue
		);
	}

	LockableCheckBoxBinding(
		JCheckBox checkBox,
		Supplier<SettingsLockTarget> targetSupplier,
		SettingsLockDraft draft,
		SettingsLockService lockService,
		Supplier<RemoteLockSnapshot> remoteLockSupplier,
		BooleanSupplier editingRemoteSubject,
		BooleanSupplier selectionEnabled,
		BooleanSupplier authoritativeValue)
	{
		this.checkBox = Objects.requireNonNull(checkBox, "checkBox");
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
		this.authoritativeValue = Objects.requireNonNull(authoritativeValue, "authoritativeValue");
		this.originalToolTip = checkBox.getToolTipText();
		Icon base = UIManager.getIcon("CheckBox.icon");
		checkBox.setIcon(new CheckBoxLockIcon(base));
		draft.addListener(this::refresh);
		refresh();
	}

	boolean handleAction(ActionEvent event)
	{
		if ((event.getModifiers() & ActionEvent.SHIFT_MASK) == 0
			|| !editingRemoteSubject.getAsBoolean())
		{
			return false;
		}
		checkBox.setSelected(authoritativeValue.getAsBoolean());
		if (selectionEnabled.getAsBoolean())
		{
			draft.toggle(target());
		}
		refresh();
		return true;
	}

	boolean isEditLocked()
	{
		return !editingRemoteSubject.getAsBoolean() && lockService.isLocked(target());
	}

	SettingsLockTarget getTarget()
	{
		return target();
	}

	void refresh()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::refresh);
			return;
		}
		String suffix;
		switch (state())
		{
			case PROPOSED:
				suffix = " Selected for post-session lock.";
				break;
			case ARMED:
				suffix = " Post-session lock armed.";
				break;
			case PERSISTENT:
				suffix = " Locked after a remote session.";
				break;
			default:
				suffix = selectionEnabled.getAsBoolean()
					? " Shift-click to select this setting for post-session locking."
					: "";
		}
		checkBox.setToolTipText((originalToolTip == null ? "" : originalToolTip) + suffix);
		checkBox.repaint();
	}

	private State state()
	{
		if (editingRemoteSubject.getAsBoolean())
		{
			RemoteLockSnapshot remote = remoteLockSupplier.get();
			if (remote.getTargets().contains(target())
				&& remote.getState() == RemoteLockState.ARMED)
			{
				return State.ARMED;
			}
			if (remote.getTargets().contains(target()) || draft.contains(target()))
			{
				return State.PROPOSED;
			}
			return State.NONE;
		}
		return lockService.isLocked(target()) ? State.PERSISTENT : State.NONE;
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

	private final class CheckBoxLockIcon implements Icon
	{
		private static final int GAP = 1;
		private static final int LOCK_WIDTH = 7;
		private static final int LOCK_HEIGHT = 9;
		private final Icon base;

		private CheckBoxLockIcon(Icon base)
		{
			this.base = base;
		}

		@Override
		public int getIconWidth()
		{
			return baseWidth() + GAP + LOCK_WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return Math.max(baseHeight(), LOCK_HEIGHT);
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			if (base != null)
			{
				base.paintIcon(component, graphics, x, y + (getIconHeight() - baseHeight()) / 2);
			}
			else
			{
				paintFallbackCheckBox(graphics, x, y);
			}
			State state = state();
			if (state == State.NONE)
			{
				return;
			}
			int lockX = x + baseWidth() + GAP;
			int lockY = y + (getIconHeight() - LOCK_HEIGHT) / 2;
			graphics.setColor(state == State.PERSISTENT ? MUTED_LOCK_COLOR : LOCK_COLOR);
			graphics.drawArc(lockX + 1, lockY, 4, 5, 0, 180);
			if (state == State.ARMED || state == State.PERSISTENT)
			{
				graphics.fillRect(lockX, lockY + 4, LOCK_WIDTH, 5);
			}
			else
			{
				graphics.drawRect(lockX, lockY + 4, LOCK_WIDTH - 1, 4);
			}
		}

		private void paintFallbackCheckBox(Graphics graphics, int x, int y)
		{
			int boxY = y + (getIconHeight() - 12) / 2;
			graphics.setColor(checkBox.isEnabled()
				? checkBox.getForeground()
				: MUTED_LOCK_COLOR);
			graphics.drawRect(x, boxY, 11, 11);
			if (checkBox.isSelected())
			{
				graphics.drawLine(x + 2, boxY + 6, x + 5, boxY + 9);
				graphics.drawLine(x + 5, boxY + 9, x + 10, boxY + 2);
			}
		}

		private int baseWidth()
		{
			return base == null ? 13 : base.getIconWidth();
		}

		private int baseHeight()
		{
			return base == null ? 13 : base.getIconHeight();
		}
	}
}
