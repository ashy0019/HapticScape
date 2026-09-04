package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteSessionManagerTest
{
	@Test
	public void participantSeedsControllerAndKeepsAcceptedChanges() throws Exception
	{
		TestRelay relay = new TestRelay();
		MutableConfig controllerConfig = new MutableConfig(12);
		MutableConfig participantConfig = new MutableConfig(47);
		MemoryStore controllerStore = new MemoryStore(controllerConfig);
		MemoryStore participantStore = new MemoryStore(participantConfig);
		EffectiveSettingsService controllerEffective =
			new EffectiveSettingsService(controllerConfig);
		EffectiveSettingsService participantEffective =
			new EffectiveSettingsService(participantConfig);

		try (RemoteSessionManager controller = new RemoteSessionManager(
			new Gson(),
			controllerStore,
			controllerEffective,
			relay);
			RemoteSessionManager participant = new RemoteSessionManager(
				new Gson(),
				participantStore,
				participantEffective,
				relay))
		{
			List<RemoteSettingsSnapshot> controllerViews = new ArrayList<>();
			controller.addListener(new RecordingListener(controllerViews));
			RemoteInvitation invitation = controller.startController(
				"wss://relay.example/relay"
			);
			participant.joinParticipant(invitation.encode());

			assertEquals(RemoteSessionState.ACTIVE, controller.getSnapshot().getState());
			assertEquals(47, last(controllerViews).getGlobalXpFeedbackSettings()
				.getIntensityPercent());
			assertEquals(47, participantEffective.current().getGlobalXpFeedbackSettings()
				.getIntensityPercent());
			assertEquals(12, controllerEffective.current().getGlobalXpFeedbackSettings()
				.getIntensityPercent());

			assertTrue(controller.updateControllerSetting(
				HapticScapeConfig.INTENSITY_PERCENT_KEY,
				68
			));
			relay.dropNextFrom(RemoteRole.PARTICIPANT);
			await(() -> participantConfig.intensityPercent() == 68);
			assertEquals("Saving changes on participant...", controller.getSnapshot().getMessage());
			controller.reconcileSettingsSafely();
			assertEquals("Saved on participant", controller.getSnapshot().getMessage());

			assertEquals(68, participantStore.capture().getGlobalXpFeedbackSettings()
				.getIntensityPercent());
			assertEquals(68, participantEffective.current().getGlobalXpFeedbackSettings()
				.getIntensityPercent());
			assertEquals(12, controllerConfig.intensityPercent());
			assertEquals(0, controllerStore.getSaveCount());

			participant.endSession();
			assertEquals(RemoteSessionState.LOCAL, participant.getSnapshot().getState());
			assertEquals(68, participantEffective.current().getGlobalXpFeedbackSettings()
				.getIntensityPercent());
		}
	}

	@Test
	public void localAndParticipantSessionsRejectControllerEdits()
	{
		MutableConfig config = new MutableConfig(35);
		MemoryStore store = new MemoryStore(config);
		try (RemoteSessionManager manager = new RemoteSessionManager(
			new Gson(),
			store,
			new EffectiveSettingsService(config),
			new TestRelay()))
		{
			assertFalse(manager.updateControllerSetting(
				HapticScapeConfig.INTENSITY_PERCENT_KEY,
				80
			));
			assertEquals(35, config.intensityPercent());
		}
	}

	private static RemoteSettingsSnapshot last(List<RemoteSettingsSnapshot> settings)
	{
		return settings.get(settings.size() - 1);
	}

	private static void await(BooleanSupplier condition) throws Exception
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline)
		{
			Thread.sleep(10);
		}
		assertTrue("Timed out waiting for remote settings", condition.getAsBoolean());
	}

	private static final class RecordingListener implements RemoteSessionListener
	{
		private final List<RemoteSettingsSnapshot> settings;

		private RecordingListener(List<RemoteSettingsSnapshot> settings)
		{
			this.settings = settings;
		}

		@Override
		public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
		{
		}

		@Override
		public void onRemoteSettingsChanged(RemoteSettingsSnapshot snapshot)
		{
			settings.add(snapshot);
		}
	}

	private static final class MutableConfig implements HapticScapeConfig
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

	private static final class MemoryStore implements RemoteSettingsStore
	{
		private final MutableConfig config;
		private int saveCount;

		private MemoryStore(MutableConfig config)
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
			config.intensity = settings.getGlobalXpFeedbackSettings().getIntensityPercent();
			saveCount++;
			return capture();
		}

		private int getSaveCount()
		{
			return saveCount;
		}
	}

	private static final class TestRelay implements RemoteTransportFactory
	{
		private final Map<RemoteRole, TestConnection> peers =
			new EnumMap<>(RemoteRole.class);
		private RemoteRole dropNextRole = RemoteRole.NONE;

		@Override
		public synchronized RemoteTransport create(RemoteTransport.Listener listener)
		{
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
			if (!sender.open)
			{
				return false;
			}
			if (sender.role == dropNextRole)
			{
				dropNextRole = RemoteRole.NONE;
				return true;
			}
			for (TestConnection peer : new ArrayList<>(peers.values()))
			{
				if (peer != sender && peer.open)
				{
					peer.listener.onMessage(message);
				}
			}
			return true;
		}

		private synchronized void dropNextFrom(RemoteRole role)
		{
			dropNextRole = role;
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
		public void connect(String relayUrl, String roomId, RemoteRole role)
		{
			relay.connect(this, role);
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
