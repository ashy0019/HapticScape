package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class RemoteLiveForgePanelTest
{
	@Test
	public void gestureIsCappedSampledAndReleasedWithoutStatusChurn() throws Exception
	{
		RecordingDispatcher dispatcher = new RecordingDispatcher();
		RemoteLiveForgePanel panel = onEdt(() -> new RemoteLiveForgePanel(dispatcher));
		try
		{
			RemotePermissions permissions = livePermissions(60);
			onEdt(() ->
			{
				panel.apply(activeController(), permissions);
				JComponent canvas = component(panel, "remoteLiveCanvas", JComponent.class);
				canvas.setSize(188, 180);
				canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, 90, 10));
				return null;
			});

			Thread.sleep(140);
			onEdt(() -> null);
			assertEquals(1, dispatcher.beginCount);
			assertEquals(60, dispatcher.lastIntensity);
			assertTrue(dispatcher.updateCount >= 2);
			assertTrue(dispatcher.updateCount <= 4);

			JTextArea warning = component(panel, "remoteLiveWarning", JTextArea.class);
			int stableHeight = warning.getPreferredSize().height;
			assertEquals("", warning.getText());
			onEdt(() ->
			{
				JComponent canvas = component(panel, "remoteLiveCanvas", JComponent.class);
				canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_RELEASED, 90, 10));
				return null;
			});

			assertEquals(1, dispatcher.endCount);
			assertEquals("", warning.getText());
			assertEquals(stableHeight, warning.getPreferredSize().height);
			onEdt(() ->
			{
				JComponent limitPanel = component(panel, "remoteLiveLimitPanel", JComponent.class);
				JComponent limitLabel = component(panel, "remoteLiveLimit", JComponent.class);
				JComponent limitBar = component(panel, "remoteLiveLimitBar", JComponent.class);
				limitPanel.setSize(180, limitPanel.getPreferredSize().height);
				limitPanel.doLayout();
				assertTrue(
					"The limit label and bar overlap",
					limitLabel.getY() + limitLabel.getHeight() < limitBar.getY()
				);
				return null;
			});
			assertTrue(
				"Preferred width was " + panel.getPreferredSize().width,
				panel.getPreferredSize().width <= 202
			);
		}
		finally
		{
			onEdt(() ->
			{
				panel.close();
				return null;
			});
		}
	}

	@Test
	public void permissionAndEmergencyStateDisableDrawingButKeepStopAvailable() throws Exception
	{
		RecordingDispatcher dispatcher = new RecordingDispatcher();
		RemoteLiveForgePanel panel = onEdt(() -> new RemoteLiveForgePanel(dispatcher));
		try
		{
			onEdt(() ->
			{
				panel.apply(activeController(), RemotePermissions.defaults());
				return null;
			});
			assertFalse(component(panel, "remoteLiveCanvas", JComponent.class).isEnabled());
			assertTrue(component(panel, "remoteLiveStop", AbstractButton.class).isEnabled());

			onEdt(() ->
			{
				panel.apply(
					new RemoteSessionSnapshot(
						RemoteRole.CONTROLLER,
						RemoteSessionState.PEER_EMERGENCY_PAUSED,
						"Participant used Emergency Off",
						1
					),
					livePermissions(60)
				);
				component(panel, "remoteLiveStop", AbstractButton.class).doClick();
				return null;
			});
			assertFalse(component(panel, "remoteLiveCanvas", JComponent.class).isEnabled());
			assertTrue(component(panel, "remoteLiveWarning", JTextArea.class)
				.getText().contains("Emergency Off"));
			assertEquals(1, dispatcher.stopCount);
		}
		finally
		{
			onEdt(() ->
			{
				panel.close();
				return null;
			});
		}
	}

	private static RemotePermissions livePermissions(int maximumIntensity)
	{
		return new RemotePermissions(
			true, true, true, true, true, false,
			maximumIntensity, 3_000, 30_000
		);
	}

	private static RemoteSessionSnapshot activeController()
	{
		return new RemoteSessionSnapshot(
			RemoteRole.CONTROLLER,
			RemoteSessionState.ACTIVE,
			"Remote control active",
			1
		);
	}

	private static MouseEvent mouse(
		Component component,
		int id,
		int x,
		int y)
	{
		return new MouseEvent(
			component,
			id,
			System.currentTimeMillis(),
			MouseEvent.BUTTON1_DOWN_MASK,
			x,
			y,
			1,
			false,
			MouseEvent.BUTTON1
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
				T nested = componentOrNull((Container) candidate, name, type);
				if (nested != null)
				{
					return nested;
				}
			}
		}
		throw new AssertionError("Missing component: " + name);
	}

	private static <T extends Component> T componentOrNull(
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
				T nested = componentOrNull((Container) candidate, name, type);
				if (nested != null)
				{
					return nested;
				}
			}
		}
		return null;
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

	private static final class RecordingDispatcher
		implements RemoteLiveForgePanel.LiveDispatcher
	{
		private int beginCount;
		private int updateCount;
		private int endCount;
		private int stopCount;
		private int lastIntensity;

		@Override
		public void begin(int intensityPercent)
		{
			beginCount++;
			lastIntensity = intensityPercent;
		}

		@Override
		public void update(int intensityPercent)
		{
			updateCount++;
			lastIntensity = intensityPercent;
		}

		@Override public void end() { endCount++; }
		@Override public void stop() { stopCount++; }
	}
}
