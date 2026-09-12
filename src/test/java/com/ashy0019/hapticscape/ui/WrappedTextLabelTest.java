package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class WrappedTextLabelTest
{
	@Test
	public void preferredHeightTracksTheDisplayedWidth() throws Exception
	{
		AtomicReference<WrappedTextLabel> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			WrappedTextLabel label = new WrappedTextLabel(
				"This explanatory text is intentionally long enough to wrap across "
					+ "several lines when the application window is narrow."
			);
			label.setSize(120, 20);
			reference.set(label);
		});
		SwingUtilities.invokeAndWait(() -> { });

		WrappedTextLabel label = reference.get();
		int narrowHeight = label.getPreferredSize().height;
		SwingUtilities.invokeAndWait(() -> label.setSize(720, 20));
		SwingUtilities.invokeAndWait(() -> { });

		assertTrue(label.getPreferredSize().height < narrowHeight);
		assertEquals(180, label.getPreferredSize().width);
	}

	@Test
	public void changedTextIsMeasuredAtTheCurrentWidth() throws Exception
	{
		AtomicReference<WrappedTextLabel> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			WrappedTextLabel label = new WrappedTextLabel(
				"A much longer initial message that needs multiple wrapped lines in a compact window."
			);
			label.setSize(140, 20);
			reference.set(label);
		});
		SwingUtilities.invokeAndWait(() -> { });

		WrappedTextLabel label = reference.get();
		int longHeight = label.getPreferredSize().height;
		SwingUtilities.invokeAndWait(() -> label.setPlainText("Ready"));

		assertTrue(label.getPreferredSize().height < longHeight);
	}
}
