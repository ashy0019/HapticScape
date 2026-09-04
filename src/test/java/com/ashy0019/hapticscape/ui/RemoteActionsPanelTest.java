package com.ashy0019.hapticscape.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticPatternSelection;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.ashy0019.hapticscape.remote.RemoteActionAcknowledgement;
import com.ashy0019.hapticscape.remote.RemotePermissions;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class RemoteActionsPanelTest
{
	@Test
	public void controllerControlsFollowPermissionsCapsAndSubjectPatterns() throws Exception
	{
		RecordingDispatcher dispatcher = new RecordingDispatcher();
		RemoteActionsPanel panel = onEdt(() -> new RemoteActionsPanel(dispatcher));
		RemotePermissions permissions = new RemotePermissions(
			true, true, false, false, true, 42, 900
		);

		onEdt(() ->
		{
			panel.apply(activeController(), permissions, subjectSettings());
			return null;
		});

		assertTrue(panel.isVisible());
		AbstractButton buzz = component(panel, "remoteBuzz", AbstractButton.class);
		AbstractButton click = component(panel, "remoteClick", AbstractButton.class);
		AbstractButton stop = component(panel, "remoteStop", AbstractButton.class);
		AbstractButton desktop = component(
			panel,
			"remoteDesktopDestination",
			AbstractButton.class
		);
		AbstractButton chatbox = component(
			panel,
			"remoteChatboxDestination",
			AbstractButton.class
		);
		AbstractButton sendMessage = component(
			panel,
			"remoteSendMessage",
			AbstractButton.class
		);
		JSlider intensity = component(panel, "remoteIntensity", JSlider.class);
		JSpinner duration = component(panel, "remoteDuration", JSpinner.class);
		JComboBox<?> pattern = component(panel, "remotePattern", JComboBox.class);
		JTextArea message = component(panel, "remoteMessage", JTextArea.class);

		assertTrue(buzz.isEnabled());
		assertFalse(click.isEnabled());
		assertTrue(stop.isEnabled());
		assertFalse(desktop.isEnabled());
		assertFalse(desktop.isSelected());
		assertTrue(chatbox.isEnabled());
		assertTrue(chatbox.isSelected());
		assertEquals(42, intensity.getMaximum());
		assertEquals(42, intensity.getValue());
		assertEquals(900, ((Number) duration.getValue()).intValue());
		assertEquals(900, ((Number) ((SpinnerNumberModel) duration.getModel())
			.getMaximum()).intValue());
		assertEquals(7, pattern.getItemCount());
		assertEquals(
			HapticPatternSelection.custom(2),
			pattern.getSelectedItem()
		);

		onEdt(() ->
		{
			buzz.doClick();
			message.setText("Good job.");
			assertTrue(sendMessage.isEnabled());
			sendMessage.doClick();
			return null;
		});

		assertEquals("CUSTOM:2", dispatcher.pattern);
		assertEquals(42, dispatcher.intensity);
		assertEquals(900, dispatcher.duration);
		assertEquals("Good job.", dispatcher.message);
		assertFalse(dispatcher.desktop);
		assertTrue(dispatcher.chatbox);
		assertEquals("", message.getText());
	}

	@Test
	public void emergencyPauseLeavesOnlyStopAvailable() throws Exception
	{
		RecordingDispatcher dispatcher = new RecordingDispatcher();
		RemoteActionsPanel panel = onEdt(() -> new RemoteActionsPanel(dispatcher));
		onEdt(() ->
		{
			panel.apply(
				new RemoteSessionSnapshot(
					RemoteRole.CONTROLLER,
					RemoteSessionState.WAITING_FOR_PEER,
					"Waiting for participant",
					0
				),
				RemotePermissions.defaults(),
				null
			);
			assertFalse(panel.isVisible());
			panel.apply(
				new RemoteSessionSnapshot(
					RemoteRole.CONTROLLER,
					RemoteSessionState.PEER_EMERGENCY_PAUSED,
					"Participant used Emergency Off",
					1
				),
				RemotePermissions.defaults(),
				subjectSettings()
			);
			return null;
		});

		assertTrue(panel.isVisible());
		assertFalse(component(panel, "remoteBuzz", AbstractButton.class).isEnabled());
		assertFalse(component(panel, "remoteClick", AbstractButton.class).isEnabled());
		assertFalse(component(panel, "remoteSendMessage", AbstractButton.class).isEnabled());
		AbstractButton stop = component(panel, "remoteStop", AbstractButton.class);
		assertTrue(stop.isEnabled());
		onEdt(() ->
		{
			stop.doClick();
			return null;
		});
		assertEquals(1, dispatcher.stopCount);

		onEdt(() ->
		{
			panel.apply(
				RemoteSessionSnapshot.local(),
				RemotePermissions.defaults(),
				null
			);
			return null;
		});
		assertFalse(panel.isVisible());
	}

	@Test
	public void messageLimitAcknowledgementAndSidebarWidthStayCompact() throws Exception
	{
		RecordingDispatcher dispatcher = new RecordingDispatcher();
		RemoteActionsPanel panel = onEdt(() -> new RemoteActionsPanel(dispatcher));
		onEdt(() ->
		{
			panel.apply(activeController(), RemotePermissions.defaults(), subjectSettings());
			JTextArea message = component(panel, "remoteMessage", JTextArea.class);
			message.setText(repeat('x', 240));
			assertEquals(200, message.getDocument().getLength());
			component(panel, "remoteBuzz", AbstractButton.class).doClick();

			RemoteActionAcknowledgement acknowledgement = new Gson().fromJson(
				"{\"actionId\":\"haptic-1\",\"result\":\"LIMITED\","
					+ "\"message\":\"Applied participant safety limits\","
					+ "\"appliedIntensityPercent\":42,\"appliedDurationMillis\":900}",
				RemoteActionAcknowledgement.class
			);
			panel.showAcknowledgement(acknowledgement);
			return null;
		});

		JTextArea status = component(panel, "remoteActionStatus", JTextArea.class);
		assertTrue(status.getText().contains("Limited"));
		assertTrue(status.getText().contains("42%, 900 ms"));
		assertTrue(
			"Preferred width was " + panel.getPreferredSize().width,
			panel.getPreferredSize().width <= 202
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

	private static RemoteSettingsSnapshot subjectSettings()
	{
		return RemoteSettingsSnapshot.capture(new HapticScapeConfig()
		{
			@Override
			public int intensityPercent()
			{
				return 90;
			}

			@Override
			public int pulseDurationMillis()
			{
				return 5_000;
			}

			@Override
			public String patternPreset()
			{
				return "CUSTOM:2";
			}

			@Override
			public String customPatterns()
			{
				return CustomPatternLibrary.defaults()
					.addBlankPattern()
					.withName(2, "Remote wave")
					.toConfigValue();
			}
		});
	}

	private static String repeat(char character, int count)
	{
		StringBuilder value = new StringBuilder(count);
		for (int index = 0; index < count; index++)
		{
			value.append(character);
		}
		return value.toString();
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
		implements RemoteActionsPanel.ActionDispatcher
	{
		private String pattern;
		private int intensity;
		private int duration;
		private String message;
		private boolean desktop;
		private boolean chatbox;
		private int stopCount;

		@Override
		public String sendHaptic(String value, int percent, int millis)
		{
			pattern = value;
			intensity = percent;
			duration = millis;
			return "haptic-1";
		}

		@Override
		public String sendClick()
		{
			return "click-1";
		}

		@Override
		public String sendMessage(String value, boolean desktopValue, boolean chatboxValue)
		{
			message = value;
			desktop = desktopValue;
			chatbox = chatboxValue;
			return "message-1";
		}

		@Override
		public String sendStop()
		{
			stopCount++;
			return "stop-1";
		}
	}
}
