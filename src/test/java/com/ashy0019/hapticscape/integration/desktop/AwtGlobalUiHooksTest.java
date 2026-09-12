package com.ashy0019.hapticscape.integration.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ashy0019.hapticscape.host.GlobalUiHooks;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class AwtGlobalUiHooksTest
{
	@Test
	public void routesToTheSuppliedHostScrollPane() throws Exception
	{
		onEdt(() ->
		{
			JPanel page = tallPage();
			JButton control = new JButton("Control");
			control.setBounds(10, 150, 120, 25);
			page.add(control);
			JScrollPane hostScroll = pageScroll(page);

			try (GlobalUiHooks.Registration ignored =
				new AwtGlobalUiHooks().installPageScrollRouting(hostScroll, page))
			{
				int before = hostScroll.getVerticalScrollBar().getValue();
				control.dispatchEvent(wheel(control, 1));

				assertTrue(hostScroll.getVerticalScrollBar().getValue() > before);
			}
			return null;
		});
	}

	@Test
	public void consumingControlsCannotBlockPageScrolling() throws Exception
	{
		onEdt(() ->
		{
			JPanel page = tallPage();
			JSlider slider = new JSlider(0, 100, 50);
			slider.setBounds(10, 150, 150, 30);
			slider.addMouseWheelListener(MouseWheelEvent::consume);
			page.add(slider);
			JScrollPane pageScroll = pageScroll(page);
			try (GlobalUiHooks.Registration ignored =
				new AwtGlobalUiHooks().installPageScrollRouting(pageScroll, page))
			{
				int before = pageScroll.getVerticalScrollBar().getValue();
				slider.dispatchEvent(wheel(slider, 1));

				assertTrue(pageScroll.getVerticalScrollBar().getValue() > before);
				assertEquals(50, slider.getValue());
			}
			return null;
		});
	}

	@Test
	public void dynamicallyAddedControlsAreRoutedToo() throws Exception
	{
		onEdt(() ->
		{
			JPanel page = tallPage();
			JScrollPane pageScroll = pageScroll(page);
			try (GlobalUiHooks.Registration ignored =
				new AwtGlobalUiHooks().installPageScrollRouting(pageScroll, page))
			{
				JButton addedLater = new JButton("Later");
				addedLater.setBounds(10, 150, 100, 25);
				page.add(addedLater);

				int before = pageScroll.getVerticalScrollBar().getValue();
				addedLater.dispatchEvent(wheel(addedLater, 1));

				assertTrue(pageScroll.getVerticalScrollBar().getValue() > before);
			}
			return null;
		});
	}

	@Test
	public void controlsOutsideTheViewportCanShareThePageScrollbar() throws Exception
	{
		onEdt(() ->
		{
			JPanel page = tallPage();
			JScrollPane pageScroll = pageScroll(page);
			JPanel wholeView = new JPanel();
			JButton header = new JButton("Back");
			wholeView.add(header);
			wholeView.add(pageScroll);

			try (GlobalUiHooks.Registration ignored =
				new AwtGlobalUiHooks().installPageScrollRouting(pageScroll, wholeView))
			{
				int before = pageScroll.getVerticalScrollBar().getValue();
				header.dispatchEvent(wheel(header, 1));
				assertTrue(pageScroll.getVerticalScrollBar().getValue() > before);
			}
			return null;
		});
	}

	@Test
	public void nestedControlScrollsNormallyThenHandsOffAtItsBoundary() throws Exception
	{
		onEdt(() ->
		{
			JPanel page = tallPage();
			JTextArea text = new JTextArea(lines(30));
			JScrollPane nested = new JScrollPane(text);
			nested.setBounds(10, 100, 160, 80);
			nested.doLayout();
			nested.getViewport().setViewSize(text.getPreferredSize());
			page.add(nested);
			JScrollPane pageScroll = pageScroll(page);
			try (GlobalUiHooks.Registration ignored =
				new AwtGlobalUiHooks().installPageScrollRouting(pageScroll, page))
			{
				int pageBefore = pageScroll.getVerticalScrollBar().getValue();
				text.dispatchEvent(wheel(text, 1));
				assertTrue(nested.getVerticalScrollBar().getValue() > 0);
				assertEquals(pageBefore, pageScroll.getVerticalScrollBar().getValue());

				nested.getVerticalScrollBar().setValue(
					nested.getVerticalScrollBar().getMaximum()
				);
				text.dispatchEvent(wheel(text, 1));
				assertTrue(pageScroll.getVerticalScrollBar().getValue() > pageBefore);
			}
			return null;
		});
	}

	private static JPanel tallPage()
	{
		JPanel page = new JPanel(null);
		page.setPreferredSize(new Dimension(180, 1_000));
		return page;
	}

	private static JScrollPane pageScroll(JPanel page)
	{
		JScrollPane scroll = new JScrollPane(
			page,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
		);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setSize(200, 200);
		scroll.doLayout();
		scroll.getViewport().setViewSize(page.getPreferredSize());
		scroll.getVerticalScrollBar().setValue(100);
		return scroll;
	}

	private static MouseWheelEvent wheel(java.awt.Component source, int rotation)
	{
		return new MouseWheelEvent(
			source,
			MouseEvent.MOUSE_WHEEL,
			System.currentTimeMillis(),
			0,
			5,
			5,
			0,
			false,
			MouseWheelEvent.WHEEL_UNIT_SCROLL,
			3,
			rotation
		);
	}

	private static String lines(int count)
	{
		StringBuilder value = new StringBuilder();
		for (int index = 0; index < count; index++)
		{
			value.append("line ").append(index).append('\n');
		}
		return value.toString();
	}

	private static <T> T onEdt(Callable<T> operation) throws Exception
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			return operation.call();
		}
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				result.set(operation.call());
			}
			catch (Exception error)
			{
				failure.set(error);
			}
		});
		if (failure.get() != null)
		{
			throw failure.get();
		}
		return result.get();
	}

}
