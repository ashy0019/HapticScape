package com.ashy0019.hapticscape.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Holds RuneLite's outer sidebar position through a queued Swing layout transaction. */
final class SidebarViewportAnchor
{
	private final JScrollBar scrollBar;
	private final int position;

	private SidebarViewportAnchor(JScrollBar scrollBar)
	{
		this.scrollBar = scrollBar;
		this.position = scrollBar.getValue();
	}

	static SidebarViewportAnchor capture(JScrollPane scrollPane)
	{
		return new SidebarViewportAnchor(
			Objects.requireNonNull(scrollPane, "scrollPane").getVerticalScrollBar()
		);
	}

	void holdThroughLayout(BooleanSupplier stillApplicable)
	{
		Objects.requireNonNull(stillApplicable, "stillApplicable");
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("Viewport transactions must begin on the EDT");
		}
		PositionGuard guard = new PositionGuard(stillApplicable);
		scrollBar.getModel().addChangeListener(guard);
		guard.restore();
		SwingUtilities.invokeLater(() ->
		{
			guard.restore();
			SwingUtilities.invokeLater(() ->
			{
				guard.restore();
				scrollBar.getModel().removeChangeListener(guard);
			});
		});
	}

	private final class PositionGuard implements ChangeListener
	{
		private final BooleanSupplier stillApplicable;
		private boolean restoring;

		private PositionGuard(BooleanSupplier stillApplicable)
		{
			this.stillApplicable = stillApplicable;
		}

		@Override
		public void stateChanged(ChangeEvent event)
		{
			restore();
		}

		private void restore()
		{
			if (restoring || !stillApplicable.getAsBoolean()
				|| scrollBar.getValue() == position)
			{
				return;
			}
			restoring = true;
			try
			{
				scrollBar.setValue(position);
			}
			finally
			{
				restoring = false;
			}
		}
	}
}
