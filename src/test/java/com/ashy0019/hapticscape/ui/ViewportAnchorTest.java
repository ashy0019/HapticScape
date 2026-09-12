package com.ashy0019.hapticscape.ui;

import java.awt.Adjustable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ViewportAnchorTest
{
	@Test
	public void holdsCapturedPositionThroughQueuedLayout() throws Exception
	{
		AtomicReference<JScrollBar> scrollBarReference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			JScrollPane scrollPane = new JScrollPane();
			JScrollBar scrollBar = new JScrollBar(
				Adjustable.VERTICAL,
				120,
				20,
				0,
				500
			);
			scrollPane.setVerticalScrollBar(scrollBar);
			ViewportAnchor anchor = ViewportAnchor.capture(scrollPane);
			anchor.holdThroughLayout(() -> true);
			scrollBar.setValue(0);
			assertEquals(120, scrollBar.getValue());
			SwingUtilities.invokeLater(() -> scrollBar.setValue(35));
			scrollBarReference.set(scrollBar);
		});
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> { });

		SwingUtilities.invokeAndWait(() ->
		{
			JScrollBar scrollBar = scrollBarReference.get();
			assertEquals(120, scrollBar.getValue());
			scrollBar.setValue(200);
			assertEquals(
				"The viewport must be released after layout",
				200,
				scrollBar.getValue()
			);
		});
	}

	@Test
	public void leavesPositionAloneWhenSessionChanged() throws Exception
	{
		AtomicReference<JScrollBar> scrollBarReference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			JScrollPane scrollPane = new JScrollPane();
			JScrollBar scrollBar = new JScrollBar(
				Adjustable.VERTICAL,
				120,
				20,
				0,
				500
			);
			scrollPane.setVerticalScrollBar(scrollBar);
			ViewportAnchor anchor = ViewportAnchor.capture(scrollPane);
			scrollBar.setValue(35);
			anchor.holdThroughLayout(() -> false);
			scrollBarReference.set(scrollBar);
		});
		SwingUtilities.invokeAndWait(() -> { });

		assertEquals(35, scrollBarReference.get().getValue());
	}
}
