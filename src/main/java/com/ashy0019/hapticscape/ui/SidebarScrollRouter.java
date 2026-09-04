package com.ashy0019.hapticscape.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseWheelEvent;
import javax.swing.BoundedRangeModel;
import javax.swing.JComboBox;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

/** Routes wheel input anywhere inside one long sidebar page to its scrollbar. */
final class SidebarScrollRouter implements AWTEventListener, AutoCloseable
{
	private final JScrollPane pageScrollPane;
	private final Component eventRoot;
	private boolean closed;

	private SidebarScrollRouter(JScrollPane pageScrollPane, Component eventRoot)
	{
		this.pageScrollPane = pageScrollPane;
		this.eventRoot = eventRoot;
		Toolkit.getDefaultToolkit().addAWTEventListener(
			this,
			AWTEvent.MOUSE_WHEEL_EVENT_MASK
		);
	}

	static SidebarScrollRouter install(JScrollPane pageScrollPane)
	{
		Component page = pageScrollPane.getViewport().getView();
		if (page == null)
		{
			throw new IllegalArgumentException("The page scroll pane must have a view");
		}
		return install(pageScrollPane, page);
	}

	static SidebarScrollRouter install(
		JScrollPane pageScrollPane,
		Component eventRoot)
	{
		if (eventRoot == null)
		{
			throw new IllegalArgumentException("The wheel event root is required");
		}
		return new SidebarScrollRouter(pageScrollPane, eventRoot);
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
		JScrollPane destination = wheelEvent.isControlDown()
			&& nested != null
			&& canScroll(nested, wheelEvent)
			? nested
			: pageScrollPane;
		scroll(wheelEvent, destination);
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
