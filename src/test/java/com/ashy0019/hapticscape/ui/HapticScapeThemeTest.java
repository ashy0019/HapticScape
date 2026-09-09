package com.ashy0019.hapticscape.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HapticScapeThemeTest
{
	@Test
	public void installsCompactDarkThemeBeforeUiConstruction() throws Exception
	{
		AtomicReference<LookAndFeel> previous = new AtomicReference<>();
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				previous.set(UIManager.getLookAndFeel());
				HapticScapeTheme.install();
			});

			assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
			assertEquals(HapticScapeTheme.CANVAS, color("Panel.background"));
			assertEquals(HapticScapeTheme.TEXT, color("Label.foreground"));
			assertEquals(HapticScapeTheme.ACCENT, color("TabbedPane.underlineColor"));
			assertEquals(HapticScapeTheme.ACCENT, color("TabbedPane.selectedForeground"));
			assertEquals(HapticScapeTheme.SURFACE, color("TabbedPane.tabAreaBackground"));
			assertEquals(HapticScapeTheme.ACCENT, color("CheckBox.icon.selectedBackground"));
			assertEquals(HapticScapeTheme.ACCENT, color("Slider.trackValueColor"));
			assertEquals(HapticScapeTheme.BORDER, color("Slider.trackColor"));
			assertEquals(HapticScapeTheme.RAISED_SURFACE, color("Slider.thumbColor"));
			assertEquals(0, UIManager.getInt("Component.arc"));
			assertEquals(0, UIManager.getInt("Button.arc"));
			assertEquals(10, UIManager.getInt("ScrollBar.width"));
		}
		finally
		{
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					UIManager.setLookAndFeel(previous.get());
				}
				catch (Exception exception)
				{
					throw new AssertionError(exception);
				}
			});
		}
	}

	private static Color color(String key)
	{
		return UIManager.getColor(key);
	}
}
