package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.TestHapticScapeSettings;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.MusicSyncSnapshot;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MusicPanelTest
{
	@Test
	public void stoppedWorkspaceStillAllowsConfiguration()
	{
		MusicPanel panel = panel(new ArrayList<>(), new ArrayList<>());
		assertFalse(panel.getSettings().isEnabled());
		assertTrue(component(panel, "musicResponse", Component.class).isEnabled());
		assertTrue(component(panel, "musicSensitivity", JSlider.class).isEnabled());
		assertTrue(component(panel, "musicMinimumIntensity", JSlider.class).isEnabled());
		assertTrue(component(panel, "musicMaximumIntensity", JSlider.class).isEnabled());
		AbstractButton action = component(panel, "musicSyncToggle", AbstractButton.class);
		assertTrue(
			action.getPreferredSize().height
				>= action.getFontMetrics(action.getFont()).getHeight()
		);
		JProgressBar meter = component(panel, "musicOutputMeter", JProgressBar.class);
		assertTrue(
			meter.getPreferredSize().height
				>= meter.getFontMetrics(meter.getFont()).getHeight() + 4
		);
	}

	@Test
	public void primaryActionStartsAndStopsCaptureSettings()
	{
		List<String> keys = new ArrayList<>();
		List<MusicSyncSettings> updates = new ArrayList<>();
		MusicPanel panel = panel(keys, updates);
		AbstractButton toggle = component(panel, "musicSyncToggle", AbstractButton.class);

		assertEquals("Start music sync", toggle.getText());
		toggle.doClick();
		assertEquals("Stop music sync", toggle.getText());
		assertTrue(updates.get(0).isEnabled());
		assertEquals(HapticScapeSettingKeys.MUSIC_SYNC_ENABLED, keys.get(0));

		toggle.doClick();
		assertEquals("Start music sync", toggle.getText());
		assertFalse(updates.get(1).isEnabled());
	}

	@Test
	public void snapshotMakesCaptureDetailAndOutputExplicit() throws Exception
	{
		MusicPanel panel = panel(new ArrayList<>(), new ArrayList<>());
		panel.updateSnapshot(new MusicSyncSnapshot(
			MusicSyncSnapshot.State.RUNNING,
			"Speakers",
			43
		));
		SwingUtilities.invokeAndWait(() -> { });

		assertEquals("Speakers",
			component(panel, "musicCaptureDetail", JLabel.class).getText());
		JProgressBar meter = component(panel, "musicOutputMeter", JProgressBar.class);
		assertEquals(43, meter.getValue());
		assertEquals("Output 43%", meter.getString());
	}

	@Test
	public void captureErrorOffersAOneClickRetry() throws Exception
	{
		List<MusicSyncSettings> updates = new ArrayList<>();
		MusicPanel panel = panel(new ArrayList<>(), updates);
		AbstractButton toggle = component(panel, "musicSyncToggle", AbstractButton.class);
		toggle.doClick();
		updates.clear();

		panel.updateSnapshot(new MusicSyncSnapshot(
			MusicSyncSnapshot.State.ERROR,
			"Unable to open system audio",
			0
		));
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals("Retry music sync", toggle.getText());

		toggle.doClick();
		assertTrue(toggle.isSelected());
		assertTrue(updates.get(0).isEnabled());
	}

	@Test
	public void remoteViewMakesEveryControlReadOnly()
	{
		MusicPanel panel = panel(new ArrayList<>(), new ArrayList<>());
		panel.setRemoteReadOnly(true);

		assertFalse(component(panel, "musicSyncToggle", Component.class).isEnabled());
		assertFalse(component(panel, "musicResponse", Component.class).isEnabled());
		assertFalse(component(panel, "musicSensitivity", Component.class).isEnabled());
		assertFalse(component(panel, "musicMinimumIntensity", Component.class).isEnabled());
		assertFalse(component(panel, "musicMaximumIntensity", Component.class).isEnabled());
	}

	private static MusicPanel panel(
		List<String> changedKeys,
		List<MusicSyncSettings> updates)
	{
		return new MusicPanel(
			new TestHapticScapeSettings(),
			(target, key, value) -> changedKeys.add(key),
			updates::add
		);
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
