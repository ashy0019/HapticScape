package com.ashy0019.hapticscape.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RemoteActionProtocolTest
{
	private static final Clock CLOCK = Clock.fixed(
		Instant.ofEpochMilli(1_800_000_000_000L),
		ZoneOffset.UTC
	);

	@Test
	public void capabilitiesActionsAndAcknowledgementsCrossEncryptedSession()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		RecordingExecutor participantExecutor = new RecordingExecutor();
		RemotePermissions participantPermissions = new RemotePermissions(
			false, true, true, false, true, 42, 900
		);
		List<RemoteActionAcknowledgement> acknowledgements = new ArrayList<>();

		try (RemoteSessionManager controller = manager(
			gson,
			new MutableConfig(20),
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			relay
		);
			RemoteSessionManager participant = manager(
				gson,
				new MutableConfig(60),
				new InMemoryRemotePermissionsStore(participantPermissions),
				participantExecutor,
				relay
			))
		{
			controller.addListener(new RemoteSessionListener()
			{
				@Override
				public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot) { }

				@Override
				public void onRemoteActionAcknowledged(RemoteActionAcknowledgement acknowledgement)
				{
					acknowledgements.add(acknowledgement);
				}
			});
			RemoteInvitation invitation = controller.startController("wss://relay.example/relay");
			participant.joinParticipant(invitation.encode());

			assertEquals(participantPermissions, controller.getPeerPermissions());
			assertFalse(controller.updateControllerSetting(
				HapticScapeConfig.INTENSITY_PERCENT_KEY,
				80
			));

			String actionId = controller.sendRemoteHaptic("DOUBLE", 90, 5_000);
			assertEquals(RemoteSessionState.ACTIVE, participant.getSnapshot().getState());
			assertEquals(1, acknowledgements.size());
			assertEquals(RemoteActionResult.LIMITED, acknowledgements.get(0).getResult());
			assertEquals(1, participantExecutor.hapticCount);
			assertEquals("DOUBLE", participantExecutor.pattern);
			assertEquals(42, participantExecutor.intensity);
			assertEquals(900, participantExecutor.duration);
			assertEquals(actionId, acknowledgements.get(0).getActionId());

			controller.sendRemoteMessage("<b>local</b>", true, true);
			assertEquals("local", participantExecutor.message);
			assertFalse(participantExecutor.desktop);
			assertTrue(participantExecutor.chatbox);
			assertEquals(2, acknowledgements.size());
			assertEquals(RemoteActionResult.LIMITED, acknowledgements.get(1).getResult());
			assertFalse(relay.containsPlaintext("REMOTE_ACTION"));
			assertFalse(relay.containsPlaintext("DOUBLE"));
			assertFalse(relay.containsPlaintext("local"));

			participant.emergencyPause();
			assertEquals(1, participantExecutor.stopCount);
			participant.endSession();
			assertEquals(2, participantExecutor.stopCount);
		}
	}

	@Test
	public void participantCanEnableSettingsWithoutControllerAuthority()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		InMemoryRemotePermissionsStore participantStore = new InMemoryRemotePermissionsStore(
			new RemotePermissions(false, true, true, true, false, 60, 3_000)
		);
		MutableConfig participantConfig = new MutableConfig(60);
		try (RemoteSessionManager controller = manager(
			gson,
			new MutableConfig(20),
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			relay
		);
			RemoteSessionManager participant = manager(
				gson,
				participantConfig,
				participantStore,
				RemoteActionExecutor.NO_OP,
				relay
			))
		{
			RemoteInvitation invitation = controller.startController("wss://relay.example/relay");
			participant.joinParticipant(invitation.encode());
			assertFalse(controller.getPeerPermissions().isSettingsAllowed());

			RemotePermissions enabled = new RemotePermissions(
				true, true, true, true, false, 60, 3_000
			);
			participant.updateLocalPermissions(enabled);
			assertEquals(enabled, controller.getPeerPermissions());
			assertTrue(controller.updateControllerSetting(
				HapticScapeConfig.INTENSITY_PERCENT_KEY,
				75
			));
		}
	}

	@Test
	public void droppedInitialParticipantFramesAreRecoveredWithoutEmergencyPause()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		relay.dropMessagesFrom(RemoteRole.PARTICIPANT);

		try (RemoteSessionManager controller = manager(
			gson,
			new MutableConfig(20),
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			relay
		);
			RemoteSessionManager participant = manager(
				gson,
				new MutableConfig(60),
				new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
				RemoteActionExecutor.NO_OP,
				relay
			))
		{
			RemoteInvitation invitation = controller.startController("wss://relay.example/relay");
			participant.joinParticipant(invitation.encode());

			assertEquals(RemoteSessionState.WAITING_FOR_PEER, controller.getSnapshot().getState());
			assertEquals(
				RemoteSessionState.WAITING_FOR_SETTINGS,
				participant.getSnapshot().getState()
			);

			relay.allowMessagesFrom(RemoteRole.PARTICIPANT);
			participant.retryHandshakeSafely();

			assertEquals(RemoteSessionState.ACTIVE, controller.getSnapshot().getState());
			assertEquals(RemoteSessionState.ACTIVE, participant.getSnapshot().getState());
		}
	}

	@Test
	public void duplicateSeedRecoversDroppedAcknowledgement()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();

		try (RemoteSessionManager controller = manager(
			gson,
			new MutableConfig(20),
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			relay
		);
			RemoteSessionManager participant = manager(
				gson,
				new MutableConfig(60),
				new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
				RemoteActionExecutor.NO_OP,
				relay
			))
		{
			relay.dropMessagesFrom(RemoteRole.CONTROLLER);
			RemoteInvitation invitation = controller.startController("wss://relay.example/relay");
			participant.joinParticipant(invitation.encode());

			assertEquals(RemoteSessionState.ACTIVE, controller.getSnapshot().getState());
			assertEquals(
				RemoteSessionState.WAITING_FOR_SETTINGS,
				participant.getSnapshot().getState()
			);

			relay.allowMessagesFrom(RemoteRole.CONTROLLER);
			participant.retryHandshakeSafely();

			assertEquals(RemoteSessionState.ACTIVE, controller.getSnapshot().getState());
			assertEquals(RemoteSessionState.ACTIVE, participant.getSnapshot().getState());
		}
	}

	@Test(expected = IllegalStateException.class)
	public void participantCannotSendControllerActions()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		try (RemoteSessionManager participant = manager(
			gson,
			new MutableConfig(60),
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			relay
		))
		{
			participant.sendRemoteClick();
		}
	}

	private static RemoteSessionManager manager(
		Gson gson,
		MutableConfig config,
		RemotePermissionsStore permissionsStore,
		RemoteActionExecutor executor,
		RemoteTransportFactory relay)
	{
		RemoteSettingsStore settingsStore = new MemorySettingsStore(config);
		return new RemoteSessionManager(
			gson,
			settingsStore,
			new EffectiveSettingsService(config),
			new SettingsLockService(gson, java.nio.file.Paths.get(
				System.getProperty("java.io.tmpdir"),
				"hapticscape-action-protocol-" + java.util.UUID.randomUUID() + ".json"
			)),
			SavedUnlockKeyStore.disabled(gson),
			permissionsStore,
			executor,
			CLOCK,
			relay
		);
	}

	private static final class RecordingExecutor implements RemoteActionExecutor
	{
		private int hapticCount;
		private String pattern;
		private int intensity;
		private int duration;
		private String message;
		private boolean desktop;
		private boolean chatbox;
		private int stopCount;

		@Override
		public void playHaptic(String value, int percent, int millis)
		{
			hapticCount++;
			pattern = value;
			intensity = percent;
			duration = millis;
		}

		@Override
		public void playClick() { }

		@Override
		public void showMessage(String value, boolean desktopValue, boolean chatboxValue)
		{
			message = value;
			desktop = desktopValue;
			chatbox = chatboxValue;
		}

		@Override
		public void stopRemoteOutput()
		{
			stopCount++;
		}
	}

	private static final class MutableConfig implements HapticScapeConfig
	{
		private int intensity;

		private MutableConfig(int intensity)
		{
			this.intensity = intensity;
		}

		@Override
		public int intensityPercent()
		{
			return intensity;
		}
	}

	private static final class MemorySettingsStore implements RemoteSettingsStore
	{
		private final MutableConfig config;

		private MemorySettingsStore(MutableConfig config)
		{
			this.config = config;
		}

		@Override
		public RemoteSettingsSnapshot capture()
		{
			return RemoteSettingsSnapshot.capture(config);
		}

		@Override
		public RemoteSettingsSnapshot save(RemoteSettingsSnapshot settings)
		{
			settings.validate();
			return settings;
		}
	}

	private static final class TestRelay implements RemoteTransportFactory
	{
		private final Map<RemoteRole, TestConnection> peers = new EnumMap<>(RemoteRole.class);
		private final List<String> wireMessages = new ArrayList<>();
		private final Map<RemoteRole, Boolean> droppedRoles = new EnumMap<>(RemoteRole.class);

		@Override
		public synchronized RemoteTransport create(RemoteTransport.Listener listener)
		{
			assertNotNull(listener);
			return new TestConnection(this, listener);
		}

		private synchronized void connect(TestConnection connection, RemoteRole role)
		{
			connection.role = role;
			connection.open = true;
			peers.put(role, connection);
			connection.listener.onOpen();
		}

		private synchronized boolean send(TestConnection sender, String message)
		{
			wireMessages.add(message);
			if (Boolean.TRUE.equals(droppedRoles.get(sender.role)))
			{
				return sender.open;
			}
			for (TestConnection peer : new ArrayList<>(peers.values()))
			{
				if (peer != sender && peer.open)
				{
					peer.listener.onMessage(message);
				}
			}
			return sender.open;
		}

		private synchronized void dropMessagesFrom(RemoteRole role)
		{
			droppedRoles.put(role, true);
		}

		private synchronized void allowMessagesFrom(RemoteRole role)
		{
			droppedRoles.remove(role);
		}

		private synchronized boolean containsPlaintext(String value)
		{
			return wireMessages.stream().anyMatch(message -> message.contains(value));
		}

		private synchronized void close(TestConnection connection)
		{
			connection.open = false;
			peers.remove(connection.role, connection);
		}
	}

	private static final class TestConnection implements RemoteTransport
	{
		private final TestRelay relay;
		private final Listener listener;
		private RemoteRole role = RemoteRole.NONE;
		private boolean open;

		private TestConnection(TestRelay relay, Listener listener)
		{
			this.relay = relay;
			this.listener = listener;
		}

		@Override
		public void connect(String relayUrl, String roomId, RemoteRole remoteRole)
		{
			relay.connect(this, remoteRole);
		}

		@Override
		public boolean send(String message)
		{
			return relay.send(this, message);
		}

		@Override
		public boolean isOpen()
		{
			return open;
		}

		@Override
		public void close()
		{
			relay.close(this);
		}
	}
}
