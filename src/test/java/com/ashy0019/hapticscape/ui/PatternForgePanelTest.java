package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PatternForgePanelTest
{
	@Test
	public void composerReflowsAroundTheShapeEditor()
	{
		assertEquals(1, PatternForgePanel.layoutModeForWidth(500));
		assertEquals(2, PatternForgePanel.layoutModeForWidth(720));
		assertEquals(2, PatternForgePanel.layoutModeForWidth(1119));
		assertEquals(3, PatternForgePanel.layoutModeForWidth(1120));
	}

	@Test
	public void canvasRemainsACompactLandscapeEditor()
	{
		PatternForgePanel panel = new PatternForgePanel(
			CustomPatternLibrary.defaults(),
			(target, key, value) -> { },
			entry -> { },
			library -> { }
		);
		try
		{
			JComponent canvas = component(panel, "patternCanvas", JComponent.class);
			assertTrue(canvas.getPreferredSize().width > canvas.getPreferredSize().height);
			assertEquals(280, canvas.getPreferredSize().height);
		}
		finally
		{
			panel.close();
		}
	}

	@Test
	public void applicationPageUsesTheScrollableViewportContract()
	{
		assertTrue(Scrollable.class.isAssignableFrom(HapticScapePanel.class));
	}

	@Test
	public void timelineLabelsFollowBeatDurationImmediately() throws Exception
	{
		PatternForgePanel panel = new PatternForgePanel(
			CustomPatternLibrary.defaults(),
			(target, key, value) -> { },
			entry -> { },
			library -> { }
		);
		try
		{
			JLabel summary = component(panel, "patternBeatSummary", JLabel.class);
			JSpinner duration = component(panel, "patternBeatDuration", JSpinner.class);
			assertEquals("One beat · 500 ms", summary.getText());
			SwingUtilities.invokeAndWait(() -> duration.setValue(1_500));
			assertEquals("One beat · 1.5 s", summary.getText());
		}
		finally
		{
			panel.close();
		}
	}

	@Test
	public void timelineOffsetsUseReadableUnits()
	{
		assertEquals("0", PatternForgePanel.formatTimelineOffset(0));
		assertEquals("375 ms", PatternForgePanel.formatTimelineOffset(375));
		assertEquals("1 s", PatternForgePanel.formatTimelineOffset(1_000));
		assertEquals("1.125 s", PatternForgePanel.formatTimelineOffset(1_125));
		assertEquals("1.5 s", PatternForgePanel.formatTimelineOffset(1_500));
	}

	private static <T extends Component> T component(
		Container root,
		String name,
		Class<T> type)
	{
		for (Component candidate : root.getComponents())
		{
			if (name.equals(candidate.getName()) && type.isInstance(candidate))
			{
				return type.cast(candidate);
			}
			if (candidate instanceof Container)
			{
				try
				{
					return component((Container) candidate, name, type);
				}
				catch (AssertionError ignored)
				{
					// Keep searching sibling containers.
				}
			}
		}
		throw new AssertionError("Missing component: " + name);
	}
}
