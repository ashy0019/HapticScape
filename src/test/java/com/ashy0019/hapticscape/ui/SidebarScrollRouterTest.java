package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Dimension;
import java.awt.event.InputEvent;
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
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

public class SidebarScrollRouterTest
{
	@Test
	public void routesToTheScrollPaneOwnedByRuneLitePluginPanel() throws Exception
	{
		onEdt(() ->
		{
			TestPluginPanel panel = new TestPluginPanel();
			panel.setLayout(null);
			panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 1_000));
			JButton control = new JButton("Control");
			control.setBounds(10, 150, 120, 25);
			panel.add(control);

			JScrollPane runeLiteScroll = panel.runeLiteScrollPane();
			runeLiteScroll.setSize(
				PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH,
				200
			);
			runeLiteScroll.doLayout();
			runeLiteScroll.getViewport().setViewSize(new Dimension(
				PluginPanel.PANEL_WIDTH,
				1_000
			));
			runeLiteScroll.getVerticalScrollBar().setValue(100);

			try (SidebarScrollRouter ignored = SidebarScrollRouter.install(
				runeLiteScroll,
				panel
			))
			{
				int before = runeLiteScroll.getVerticalScrollBar().getValue();
				control.dispatchEvent(wheel(control, 1));

				assertTrue(runeLiteScroll.getVerticalScrollBar().getValue() > before);
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
			try (SidebarScrollRouter ignored = SidebarScrollRouter.install(pageScroll))
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
			try (SidebarScrollRouter ignored = SidebarScrollRouter.install(pageScroll))
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

			try (SidebarScrollRouter ignored = SidebarScrollRouter.install(
				pageScroll,
				wholeView
			))
			{
				int before = pageScroll.getVerticalScrollBar().getValue();
				header.dispatchEvent(wheel(header, 1));
				assertTrue(pageScroll.getVerticalScrollBar().getValue() > before);
			}
			return null;
		});
	}

	@Test
	public void textAreaRoutesNormallyAndUsesControlWheelForNestedScrolling() throws Exception
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
			try (SidebarScrollRouter ignored = SidebarScrollRouter.install(pageScroll))
			{
				int pageBefore = pageScroll.getVerticalScrollBar().getValue();
				text.dispatchEvent(wheel(text, 1));
				assertEquals(0, nested.getVerticalScrollBar().getValue());
				assertTrue(pageScroll.getVerticalScrollBar().getValue() > pageBefore);

				int pageAfterNormalWheel = pageScroll.getVerticalScrollBar().getValue();
				text.dispatchEvent(wheel(text, 1, InputEvent.CTRL_DOWN_MASK));
				assertTrue(nested.getVerticalScrollBar().getValue() > 0);
				assertEquals(
					pageAfterNormalWheel,
					pageScroll.getVerticalScrollBar().getValue()
				);
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
		return wheel(source, rotation, 0);
	}

	private static MouseWheelEvent wheel(
		java.awt.Component source,
		int rotation,
		int modifiers)
	{
		return new MouseWheelEvent(
			source,
			MouseEvent.MOUSE_WHEEL,
			System.currentTimeMillis(),
			modifiers,
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

	private static final class TestPluginPanel extends PluginPanel
	{
		private JScrollPane runeLiteScrollPane()
		{
			return getScrollPane();
		}
	}
}
