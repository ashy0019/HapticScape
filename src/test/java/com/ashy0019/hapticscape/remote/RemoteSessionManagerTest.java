package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.CustomPattern;
import com.ashy0019.hapticscape.CustomPatternLibrary;
import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteSessionManagerTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
		SettingsLockService controllerLock = lockService("controller-lock.json");
		SettingsLockService participantLock = lockService("participant-lock.json");

		try (RemoteSessionManager controller = new RemoteSessionManager(
			new Gson(),
			controllerStore,
			controllerEffective,
			controllerLock,
			relay);
			RemoteSessionManager participant = new RemoteSessionManager(
				new Gson(),
				participantStore,
				participantEffective,
				participantLock,
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

			CustomPatternLibrary subjectPatterns = CustomPatternLibrary.defaults()
				.addBlankPattern()
				.withName(2, "Remote forge")
				.withPattern(2, new CustomPattern(0, 60, 100, 0), 700, 3);
			String persistedPatterns = subjectPatterns.toConfigValue();
			assertTrue(controller.updateControllerSetting(
				HapticScapeConfig.CUSTOM_PATTERNS_KEY,
				persistedPatterns
			));
			await(() -> participantConfig.customPatterns().equals(persistedPatterns));
			controller.reconcileSettingsSafely();
			assertEquals(
				"Remote forge",
				participantStore.capture().getCustomPatterns().findById(2)
					.orElseThrow(AssertionError::new)
					.getName()
			);

			participant.endSession();
			assertEquals(RemoteSessionState.LOCAL, participant.getSnapshot().getState());
			assertEquals(68, participantEffective.current().getGlobalXpFeedbackSettings()
				.getIntensityPercent());
			assertEquals(persistedPatterns, participantConfig.customPatterns());
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
			lockService("single-lock.json"),
			new TestRelay()))
		{
			assertFalse(manager.updateControllerSetting(
				HapticScapeConfig.INTENSITY_PERCENT_KEY,
				80
			));
			assertEquals(35, config.intensityPercent());
		}
	}

	@Test
	public void participantApprovalPersistsLockAfterSession()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		MutableConfig controllerConfig = new MutableConfig(20);
		MutableConfig participantConfig = new MutableConfig(60);
		SettingsLockService controllerLock = lockService("approval-controller.json");
		SettingsLockService participantLock = lockService("approval-participant.json");
		char[] password = "correct horse battery staple".toCharArray();
		try (RemoteSessionManager controller = new RemoteSessionManager(
			gson,
			new MemoryStore(controllerConfig),
			new EffectiveSettingsService(controllerConfig),
			controllerLock,
			relay);
			RemoteSessionManager participant = new RemoteSessionManager(
				gson,
				new MemoryStore(participantConfig),
				new EffectiveSettingsService(participantConfig),
				participantLock,
				relay))
		{
			RemoteInvitation invitation = controller.startController(
				"wss://relay.example/relay"
			);
			participant.joinParticipant(invitation.encode());
			controller.proposeSettingsLock(password);

			assertEquals(
				RemoteLockState.APPROVAL_REQUIRED,
				participant.getLockSnapshot().getState()
			);
			assertFalse(participantLock.isLocked());
			participant.acceptPendingSettingsLock();

			assertEquals(RemoteLockState.ARMED, controller.getLockSnapshot().getState());
			assertEquals(RemoteLockState.ARMED, participant.getLockSnapshot().getState());
			assertTrue(participantLock.isLocked());
			participant.endSession();
			assertTrue(participantLock.isLocked());
			boolean rejoinRejected = false;
			try
			{
				participant.joinParticipant(invitation.encode());
			}
			catch (IllegalStateException expected)
			{
				rejoinRejected = true;
			}
			assertTrue(rejoinRejected);
			assertFalse(participantLock.unlock("wrong password".toCharArray()));
			assertTrue(participantLock.isLocked());
			assertTrue(participantLock.unlock(password));
			assertFalse(participantLock.isLocked());
			assertFalse(controllerLock.isLocked());
		}
		finally
		{
			java.util.Arrays.fill(password, '\0');
		}
	}

	@Test
	public void controllerCanCancelAnAcceptedSessionLock()
	{
		Gson gson = new Gson();
		TestRelay relay = new TestRelay();
		MutableConfig controllerConfig = new MutableConfig(20);
		MutableConfig participantConfig = new MutableConfig(60);
		SettingsLockService participantLock = lockService("cancel-participant.json");
		try (RemoteSessionManager controller = new RemoteSessionManager(
			gson,
			new MemoryStore(controllerConfig),
			new EffectiveSettingsService(controllerConfig),
			lockService("cancel-controller.json"),
			relay);
			RemoteSessionManager participant = new RemoteSessionManager(
				gson,
				new MemoryStore(participantConfig),
				new EffectiveSettingsService(participantConfig),
				participantLock,
				relay))
		{
			RemoteInvitation invitation = controller.startController(
				"wss://relay.example/relay"
			);
			participant.joinParticipant(invitation.encode());
			controller.proposeSettingsLock("cancel this password".toCharArray());
			participant.acceptPendingSettingsLock();
			assertTrue(participantLock.isLocked());

			controller.cancelSettingsLock();

			assertFalse(participantLock.isLocked());
			assertEquals(RemoteLockState.INACTIVE, controller.getLockSnapshot().getState());
			assertEquals(RemoteLockState.INACTIVE, participant.getLockSnapshot().getState());
		}
	}

	private SettingsLockService lockService(String name)
	{
		return new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve(name)
		);
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
		private volatile String customPatterns =
			CustomPatternLibrary.defaults().toConfigValue();

		private MutableConfig(int intensity)
		{
			this.intensity = intensity;
		}

		@Override
		public int intensityPercent()
		{
			return intensity;
		}

		@Override
		public String customPatterns()
		{
			return customPatterns;
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
			config.customPatterns = settings.getCustomPatterns().toConfigValue();
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
