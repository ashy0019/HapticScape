package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.host.GlobalUiHooks;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BoundedRangeModel;
import javax.swing.JComboBox;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** AWT implementation of process-wide desktop UI hooks. */
public final class AwtGlobalUiHooks implements GlobalUiHooks
{
	@Override
	public Registration installPageScrollRouting(
		JScrollPane pageScrollPane,
		Component eventRoot)
	{
		Objects.requireNonNull(pageScrollPane, "pageScrollPane");
		Objects.requireNonNull(eventRoot, "eventRoot");
		return new PageScrollRegistration(pageScrollPane, eventRoot);
	}

	@Override
	public Registration onGestureEnd(Runnable listener)
	{
		Objects.requireNonNull(listener, "listener");
		AWTEventListener awtListener = event ->
		{
			if (event instanceof MouseEvent
				&& event.getID() == MouseEvent.MOUSE_RELEASED)
			{
				listener.run();
			}
			else if (event instanceof WindowEvent
				&& event.getID() == WindowEvent.WINDOW_LOST_FOCUS)
			{
				listener.run();
			}
		};
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		toolkit.addAWTEventListener(
			awtListener,
			AWTEvent.MOUSE_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK
		);
		return once(() -> toolkit.removeAWTEventListener(awtListener));
	}

	@Override
	public Registration onScopedKeyPress(
		Component scope,
		Consumer<ScopedKeyPress> listener)
	{
		Objects.requireNonNull(scope, "scope");
		Objects.requireNonNull(listener, "listener");
		KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		KeyEventDispatcher dispatcher = event ->
		{
			if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed())
			{
				return false;
			}
			Window scopeWindow = SwingUtilities.getWindowAncestor(scope);
			if (scopeWindow == null || focusManager.getActiveWindow() != scopeWindow)
			{
				return false;
			}
			listener.accept(new ScopedKeyPress(
				event.getKeyCode(),
				focusManager.getFocusOwner() instanceof JTextComponent
			));
			return false;
		};
		focusManager.addKeyEventDispatcher(dispatcher);
		return once(() -> focusManager.removeKeyEventDispatcher(dispatcher));
	}

	private static Registration once(Runnable closeAction)
	{
		return new Registration()
		{
			private boolean closed;

			@Override
			public void close()
			{
				if (!closed)
				{
					closed = true;
					closeAction.run();
				}
			}
		};
	}

	private static final class PageScrollRegistration
		implements AWTEventListener, Registration
	{
		private final JScrollPane pageScrollPane;
		private final Component eventRoot;
		private boolean closed;

		private PageScrollRegistration(JScrollPane pageScrollPane, Component eventRoot)
		{
			this.pageScrollPane = pageScrollPane;
			this.eventRoot = eventRoot;
			Toolkit.getDefaultToolkit().addAWTEventListener(
				this,
				AWTEvent.MOUSE_WHEEL_EVENT_MASK
			);
		}

		@Override
		public void eventDispatched(AWTEvent event)
		{
			if (closed || !(event instanceof MouseWheelEvent))
			{
				return;
			}
			MouseWheelEvent wheelEvent = (MouseWheelEvent) event;
			if (wheelEvent.getPreciseWheelRotation() == 0.0
				|| !isInsidePage(wheelEvent.getComponent()))
			{
				return;
			}

			JComboBox<?> comboBox = comboBoxAncestor(wheelEvent.getComponent());
			if (comboBox != null && comboBox.isPopupVisible())
			{
				return;
			}

			JScrollPane nested = nestedScrollPane(wheelEvent.getComponent());
			if (nested != null && canScroll(nested, wheelEvent))
			{
				// Wheel events dispatched to a text or list child do not reliably
				// bubble to its enclosing scroll pane, so route this explicitly.
				scroll(wheelEvent, nested);
				return;
			}
			scroll(wheelEvent, pageScrollPane);
		}

		@Override
		public void close()
		{
			if (!closed)
			{
				closed = true;
				Toolkit.getDefaultToolkit().removeAWTEventListener(this);
			}
		}

		private boolean isInsidePage(Component source)
		{
			Component current = source;
			while (current != null)
			{
				if (current == eventRoot)
				{
					return true;
				}
				current = current.getParent();
			}
			return false;
		}

		private JScrollPane nestedScrollPane(Component source)
		{
			Component current = source;
			while (current != null && current != eventRoot)
			{
				if (current instanceof JScrollPane && current != pageScrollPane)
				{
					return (JScrollPane) current;
				}
				current = current.getParent();
			}
			return null;
		}

		private static JComboBox<?> comboBoxAncestor(Component source)
		{
			Component current = source;
			while (current != null)
			{
				if (current instanceof JComboBox)
				{
					return (JComboBox<?>) current;
				}
				current = current.getParent();
			}
			return null;
		}

		private static boolean canScroll(JScrollPane scrollPane, MouseWheelEvent event)
		{
			if (!scrollPane.isWheelScrollingEnabled()
				|| scrollPane.getVerticalScrollBarPolicy() == JScrollPane.VERTICAL_SCROLLBAR_NEVER)
			{
				return false;
			}
			JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
			BoundedRangeModel model = scrollBar.getModel();
			return event.getPreciseWheelRotation() < 0.0
				? model.getValue() > model.getMinimum()
				: model.getValue() + model.getExtent() < model.getMaximum();
		}

		private static void scroll(MouseWheelEvent event, JScrollPane destination)
		{
			int direction = event.getPreciseWheelRotation() < 0.0 ? -1 : 1;
			JScrollBar scrollBar = destination.getVerticalScrollBar();
			int distance;
			if (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL)
			{
				distance = scrollBar.getBlockIncrement(direction);
			}
			else
			{
				int units = Math.max(1, Math.abs(event.getUnitsToScroll()));
				distance = scrollBar.getUnitIncrement(direction) * units;
			}
			scrollBar.setValue(scrollBar.getValue() + direction * distance);
			event.consume();
		}
	}
}
