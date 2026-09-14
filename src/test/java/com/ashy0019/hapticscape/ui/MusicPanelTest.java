package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.TestHapticScapeSettings;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.MusicSyncSnapshot;
import com.ashy0019.hapticscape.music.AudioCaptureEndpoint;
import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureMode;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JComboBox;
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

	@Test
	public void localAudioSelectionPersistsOpaqueIdAndRestartsCapture() throws Exception
	{
		List<String> keys = new ArrayList<>();
		List<AudioCaptureEndpoint> selected = new ArrayList<>();
		CountDownLatch enumerated = new CountDownLatch(1);
		MusicPanel panel = new MusicPanel(
			new TestHapticScapeSettings(),
			(target, key, value) -> { },
			(target, key, value) -> keys.add(key + "=" + value),
			ignored -> { },
			() ->
			{
				try
				{
					return java.util.Arrays.asList(
						AudioCaptureEndpoint.systemDefault(),
						new AudioCaptureEndpoint("endpoint-2", "Music channel")
					);
				}
				finally
				{
					enumerated.countDown();
				}
			},
			selected::add
		);
		enumerated.await();
		SwingUtilities.invokeAndWait(() -> { });
		AudioCaptureEndpoint music = new AudioCaptureEndpoint(
			"endpoint-2",
			"Music channel"
		);
		SwingUtilities.invokeAndWait(() ->
			component(panel, "musicAudioSource", JComboBox.class).setSelectedItem(music)
		);

		assertTrue(keys.contains(
			HapticScapeSettingKeys.MUSIC_CAPTURE_ENDPOINT_ID + "=endpoint-2"
		));
		assertTrue(keys.contains(
			HapticScapeSettingKeys.MUSIC_CAPTURE_ENDPOINT_NAME + "=Music channel"
		));
		assertEquals(music, selected.get(selected.size() - 1));
	}

	@Test
	public void controllerSubjectViewDoesNotExposeEndpointPicker()
	{
		MusicPanel panel = panel(new ArrayList<>(), new ArrayList<>());
		panel.setCaptureSourceRemote(true);

		assertFalse(component(
			panel,
			"musicAudioSourceLocalControls",
			Component.class
		).isVisible());
		assertTrue(component(
			panel,
			"musicAudioSourceRemoteNotice",
			Component.class
		).isVisible());
	}

	@Test
	public void applicationModeListsMixerAppsAndPersistsLocalSelection() throws Exception
	{
		List<String> localChanges = new ArrayList<>();
		List<AudioCaptureMode> modes = new ArrayList<>();
		List<AudioCaptureApplication> applications = new ArrayList<>();
		CountDownLatch scanned = new CountDownLatch(1);
		CountDownLatch applied = new CountDownLatch(1);
		MusicPanel panel = new MusicPanel(
			new TestHapticScapeSettings(),
			(target, key, value) -> { },
			(target, key, value) -> localChanges.add(key + "=" + value),
			ignored -> { },
			() -> java.util.Collections.singletonList(AudioCaptureEndpoint.systemDefault()),
			ignored -> { }
		);
		panel.configureApplicationCapture(
			() ->
			{
				try
				{
					return java.util.Arrays.asList(
						new AudioCaptureApplication("command:spotify.exe", "Spotify"),
						new AudioCaptureApplication("command:client.exe", "Game client")
					);
				}
				finally
				{
					scanned.countDown();
				}
			},
			modes::add,
			application ->
			{
				applications.add(application);
				applied.countDown();
			}
		);

		SwingUtilities.invokeAndWait(() -> component(
			panel,
			"musicCaptureMode",
			JComboBox.class
		).setSelectedItem(AudioCaptureMode.APPLICATION));
		scanned.await();
		assertTrue(applied.await(5, TimeUnit.SECONDS));

		assertEquals(AudioCaptureMode.APPLICATION, modes.get(modes.size() - 1));
		assertTrue(localChanges.contains(
			HapticScapeSettingKeys.MUSIC_CAPTURE_MODE + "=APPLICATION"
		));
		assertEquals(2, component(
			panel,
			"musicAudioApplication",
			JComboBox.class
		).getItemCount());
		assertFalse(applications.isEmpty());
		assertTrue(localChanges.stream().anyMatch(change -> change.startsWith(
			HapticScapeSettingKeys.MUSIC_CAPTURE_APPLICATION_ID + "="
		)));
	}

	@Test
	public void savedSourceIsVisibleWhileWindowsScanIsStillRunning() throws Exception
	{
		CountDownLatch scanStarted = new CountDownLatch(1);
		CountDownLatch releaseScan = new CountDownLatch(1);
		MusicPanel panel = new MusicPanel(
			new TestHapticScapeSettings(),
			(target, key, value) -> { },
			(target, key, value) -> { },
			ignored -> { },
			() ->
			{
				scanStarted.countDown();
				try
				{
					releaseScan.await();
				}
				catch (InterruptedException failure)
				{
					Thread.currentThread().interrupt();
				}
				return java.util.Collections.singletonList(
					AudioCaptureEndpoint.systemDefault()
				);
			},
			ignored -> { }
		);
		scanStarted.await();

		assertEquals(
			AudioCaptureEndpoint.systemDefault(),
			component(panel, "musicAudioSource", JComboBox.class).getSelectedItem()
		);
		releaseScan.countDown();
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
