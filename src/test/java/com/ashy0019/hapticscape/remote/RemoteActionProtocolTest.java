package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.TestHapticScapeSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.Test;

public class RemoteActionProtocolTest
{
	private static final Clock CLOCK = Clock.fixed(
		Instant.ofEpochMilli(1_800_000_000_000L),
		ZoneOffset.UTC
	);

	@Test
	public void capabilitiesActionsAndAcknowledgementsCrossEncryptedSession()
		throws Exception
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		RecordingExecutor participantExecutor = new RecordingExecutor();
		RemotePermissions participantPermissions = new RemotePermissions(
			false, true, true, false, true, 42, 900
		);
		List<RemoteActionAcknowledgement> acknowledgements =
			new CopyOnWriteArrayList<>();

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

			awaitActive(controller, participant);
			await(() -> participantPermissions.equals(controller.getPeerPermissions()));
			assertFalse(controller.updateControllerSetting(
				HapticScapeSettingKeys.INTENSITY_PERCENT,
				80
			));

			String actionId = controller.sendRemoteHaptic("DOUBLE", 90, 5_000);
			await(() -> acknowledgements.size() == 1
				&& participantExecutor.hapticCount == 1);
			assertEquals(RemoteSessionState.ACTIVE, participant.getSnapshot().getState());
			assertEquals(1, acknowledgements.size());
			assertEquals(RemoteActionResult.LIMITED, acknowledgements.get(0).getResult());
			assertEquals(1, participantExecutor.hapticCount);
			assertEquals("DOUBLE", participantExecutor.pattern);
			assertEquals(42, participantExecutor.intensity);
			assertEquals(900, participantExecutor.duration);
			assertEquals(actionId, acknowledgements.get(0).getActionId());

			controller.sendRemoteMessage("<b>local</b>", true, true);
			await(() -> acknowledgements.size() == 2
				&& "local".equals(participantExecutor.message));
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
		throws Exception
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		RemotePermissions initialPermissions = new RemotePermissions(
			false, true, true, true, false, 60, 3_000
		);
		InMemoryRemotePermissionsStore participantStore = new InMemoryRemotePermissionsStore(
			initialPermissions
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
			awaitActive(controller, participant);
			await(() -> initialPermissions.equals(controller.getPeerPermissions()));
			assertFalse(controller.getPeerPermissions().isSettingsAllowed());

			RemotePermissions enabled = new RemotePermissions(
				true, true, true, true, false, 60, 3_000
			);
			participant.updateLocalPermissions(enabled);
			await(() -> enabled.equals(controller.getPeerPermissions()));
			assertTrue(controller.updateControllerSetting(
				HapticScapeSettingKeys.INTENSITY_PERCENT,
				75
			));
		}
	}

	@Test
	public void liveForgeStreamsEncryptedClampedSamplesAndRelease()
		throws Exception
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		RecordingExecutor participantExecutor = new RecordingExecutor();
		RemotePermissions participantPermissions = new RemotePermissions(
			true, true, true, true, true, false,
			42, 900, 30_000
		);

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
			RemoteInvitation invitation = controller.startController(
				"wss://relay.example/relay"
			);
			participant.joinParticipant(invitation.encode());
			awaitActive(controller, participant);
			await(() -> participantPermissions.equals(controller.getPeerPermissions()));

			controller.beginRemoteLiveHaptic(90);
			controller.updateRemoteLiveHaptic(30);
			controller.endRemoteLiveHaptic();

			await(() -> participantExecutor.liveIntensities.size() == 2
				&& participantExecutor.liveReleaseCount == 1);
			assertEquals(java.util.Arrays.asList(42, 30), participantExecutor.liveIntensities);
			assertEquals(1, participantExecutor.liveReleaseCount);
			assertFalse(relay.containsPlaintext("REMOTE_LIVE_HAPTIC"));
			assertFalse(relay.containsPlaintext("streamId"));
		}
	}

	@Test
	public void droppedInitialParticipantFramesAreRecoveredWithoutEmergencyPause()
		throws Exception
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

			await(() -> controller.getSnapshot().getState()
					== RemoteSessionState.WAITING_FOR_PEER
				&& participant.getSnapshot().getState()
					== RemoteSessionState.WAITING_FOR_SETTINGS);

			relay.allowMessagesFrom(RemoteRole.PARTICIPANT);
			participant.retryHandshakeSafely();

			awaitActive(controller, participant);
		}
	}

	@Test
	public void duplicateSeedRecoversDroppedAcknowledgement()
		throws Exception
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

			await(() -> controller.getSnapshot().getState() == RemoteSessionState.ACTIVE
				&& participant.getSnapshot().getState()
					== RemoteSessionState.WAITING_FOR_SETTINGS);

			relay.allowMessagesFrom(RemoteRole.CONTROLLER);
			participant.retryHandshakeSafely();

			awaitActive(controller, participant);
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

	private static void awaitActive(
		RemoteSessionManager controller,
		RemoteSessionManager participant) throws Exception
	{
		await(() -> controller.getSnapshot().getState() == RemoteSessionState.ACTIVE
			&& participant.getSnapshot().getState() == RemoteSessionState.ACTIVE);
	}

	private static void await(BooleanSupplier condition) throws Exception
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline)
		{
			Thread.sleep(10);
		}
		assertTrue(
			"Timed out waiting for remote protocol state",
			condition.getAsBoolean()
		);
	}

	private static final class RecordingExecutor implements RemoteActionExecutor
	{
		private volatile int hapticCount;
		private volatile String pattern;
		private volatile int intensity;
		private volatile int duration;
		private volatile String message;
		private volatile boolean desktop;
		private volatile boolean chatbox;
		private volatile int stopCount;
		private final List<Integer> liveIntensities = new CopyOnWriteArrayList<>();
		private volatile int liveReleaseCount;

		@Override
		public void playHaptic(String value, int percent, int millis)
		{
			hapticCount++;
			pattern = value;
			intensity = percent;
			duration = millis;
		}

		@Override
		public void setRemoteLiveIntensity(int percent)
		{
			liveIntensities.add(percent);
		}

		@Override
		public void releaseRemoteLiveHaptic()
		{
			liveReleaseCount++;
		}

		@Override
		public void stopRemoteLiveHaptic() { }

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

	private static final class MutableConfig extends TestHapticScapeSettings
	{
		private volatile int intensity;

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
		private final ArrayDeque<Delivery> deliveries = new ArrayDeque<>();
		private boolean delivering;

		@Override
		public synchronized RemoteTransport create(RemoteTransport.Listener listener)
		{
			assertNotNull(listener);
			return new TestConnection(this, listener);
		}

		private void connect(TestConnection connection, RemoteRole role)
		{
			synchronized (this)
			{
				connection.role = role;
				connection.open = true;
				peers.put(role, connection);
			}
			connection.listener.onOpen();
		}

		private boolean send(TestConnection sender, String message)
		{
			boolean senderOpen;
			boolean shouldDrain;
			synchronized (this)
			{
				wireMessages.add(message);
				senderOpen = sender.open;
				if (Boolean.TRUE.equals(droppedRoles.get(sender.role)))
				{
					return senderOpen;
				}
				for (TestConnection peer : peers.values())
				{
					if (peer != sender && peer.open)
					{
						deliveries.addLast(new Delivery(peer, message));
					}
				}
				shouldDrain = !delivering;
				if (shouldDrain)
				{
					delivering = true;
				}
			}
			if (shouldDrain)
			{
				drainDeliveries();
			}
			return senderOpen;
		}

		private void drainDeliveries()
		{
			while (true)
			{
				Delivery delivery;
				synchronized (this)
				{
					delivery = deliveries.pollFirst();
					if (delivery == null)
					{
						delivering = false;
						return;
					}
					if (!delivery.recipient.open)
					{
						continue;
					}
				}
				try
				{
					delivery.recipient.listener.onMessage(delivery.message);
				}
				catch (RuntimeException | Error failure)
				{
					synchronized (this)
					{
						deliveries.clear();
						delivering = false;
					}
					throw failure;
				}
			}
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
			deliveries.removeIf(delivery -> delivery.recipient == connection);
		}

		private static final class Delivery
		{
			private final TestConnection recipient;
			private final String message;

			private Delivery(TestConnection recipient, String message)
			{
				this.recipient = recipient;
				this.message = message;
			}
		}
	}

	private static final class TestConnection implements RemoteTransport
	{
		private final TestRelay relay;
		private final Listener listener;
		private RemoteRole role = RemoteRole.NONE;
		private volatile boolean open;

		private TestConnection(TestRelay relay, Listener listener)
		{
			this.relay = relay;
			this.listener = listener;
		}

		@Override
		public void connect(
			String relayUrl,
			String roomId,
			RemoteRole remoteRole,
			String reconnectSlot)
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
