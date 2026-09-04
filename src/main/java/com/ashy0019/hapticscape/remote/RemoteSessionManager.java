package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;

/**
 * Coordinates one consented Remote Control session.
 *
 * <p>The relay transports only encrypted application messages. The participant
 * never connects directly to the controller, and the controller never receives
 * access to the participant's Intiface connection.</p>
 */
public final class RemoteSessionManager implements AutoCloseable
{
	private static final Logger LOG = Logger.getLogger(RemoteSessionManager.class.getName());
	private static final long HOUSEKEEPING_INTERVAL_MILLIS = 500;
	private static final long SETTINGS_DEBOUNCE_MILLIS = 40;
	private static final long SETTINGS_RECONCILE_INTERVAL_MILLIS = 5_000;
	private static final long HELLO_INTERVAL_MILLIS = 1_000;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final Gson gson;
	private final RemoteSettingsStore settingsStore;
	private final EffectiveSettingsService effectiveSettings;
	private final RemoteTransportFactory transportFactory;
	private final SecureRandom random = new SecureRandom();
	private final ScheduledExecutorService scheduler;
	private final CopyOnWriteArrayList<RemoteSessionListener> listeners =
		new CopyOnWriteArrayList<>();

	private volatile RemoteSessionSnapshot snapshot = RemoteSessionSnapshot.local();
	private volatile RemoteTransport relayClient;
	private volatile RemoteRole role = RemoteRole.NONE;
	private volatile RemoteCrypto crypto;
	private volatile RemoteInvitation invitation;
	private volatile RemoteSettingsSnapshot lastSentSettings;
	private volatile RemoteSettingsSnapshot controllerSettings;
	private volatile long lastHelloNanos;
	private volatile long nextSettingsVersion;
	private volatile long lastReceivedSettingsVersion;
	private volatile long lastAcknowledgedVersion;
	private ScheduledFuture<?> pendingSettingsSync;
	private volatile boolean closed;

	public RemoteSessionManager(
		OkHttpClient httpClient,
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings)
	{
		this(
			gson,
			settingsStore,
			effectiveSettings,
			listener -> new RemoteRelayClient(
				Objects.requireNonNull(httpClient, "httpClient"),
				listener
			)
		);
	}

	RemoteSessionManager(
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings,
		RemoteTransportFactory transportFactory)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
		this.effectiveSettings = Objects.requireNonNull(effectiveSettings, "effectiveSettings");
		this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
		this.scheduler = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-remote");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.scheduleAtFixedRate(
			this::tickSafely,
			HOUSEKEEPING_INTERVAL_MILLIS,
			HOUSEKEEPING_INTERVAL_MILLIS,
			TimeUnit.MILLISECONDS
		);
		scheduler.scheduleAtFixedRate(
			this::reconcileSettingsSafely,
			SETTINGS_RECONCILE_INTERVAL_MILLIS,
			SETTINGS_RECONCILE_INTERVAL_MILLIS,
			TimeUnit.MILLISECONDS
		);
	}

	public RemoteSessionSnapshot getSnapshot()
	{
		return snapshot;
	}

	public void addListener(RemoteSessionListener listener)
	{
		RemoteSessionListener required = Objects.requireNonNull(listener, "listener");
		listeners.add(required);
		required.onRemoteSessionChanged(snapshot);
	}

	public void removeListener(RemoteSessionListener listener)
	{
		listeners.remove(listener);
	}

	public synchronized boolean updateControllerSetting(String key, Object value)
	{
		if (role != RemoteRole.CONTROLLER
			|| controllerSettings == null
			|| (snapshot.getState() != RemoteSessionState.ACTIVE
				&& snapshot.getState() != RemoteSessionState.PEER_EMERGENCY_PAUSED))
		{
			return false;
		}
		controllerSettings = controllerSettings.withConfigurationValue(gson, key, value);

		if (pendingSettingsSync != null)
		{
			pendingSettingsSync.cancel(false);
		}
		pendingSettingsSync = scheduler.schedule(
			this::sendDebouncedSettingsSafely,
			SETTINGS_DEBOUNCE_MILLIS,
			TimeUnit.MILLISECONDS
		);
		publish(snapshot.getState(), "Saving changes on participant...");
		return true;
	}

	public boolean isControllerSession()
	{
		return role == RemoteRole.CONTROLLER
			&& snapshot.getState() != RemoteSessionState.LOCAL;
	}

	public synchronized RemoteInvitation startController(String relayUrl)
	{
		requireOpen();
		endSessionInternal(false, "Starting a new remote session");

		byte[] roomBytes = new byte[12];
		byte[] keyBytes = new byte[32];
		random.nextBytes(roomBytes);
		random.nextBytes(keyBytes);
		RemoteInvitation created = new RemoteInvitation(
			relayUrl,
			ENCODER.encodeToString(roomBytes),
			ENCODER.encodeToString(keyBytes)
		);
		beginSession(RemoteRole.CONTROLLER, created);
		return created;
	}

	public synchronized void joinParticipant(String encodedInvitation)
	{
		requireOpen();
		endSessionInternal(false, "Joining a new remote session");
		beginSession(RemoteRole.PARTICIPANT, RemoteInvitation.parse(encodedInvitation));
	}

	public synchronized void emergencyPause()
	{
		if (role != RemoteRole.PARTICIPANT
			|| snapshot.getState() == RemoteSessionState.LOCAL
			|| snapshot.getState() == RemoteSessionState.EMERGENCY_PAUSED)
		{
			return;
		}
		publish(RemoteSessionState.EMERGENCY_PAUSED, "Emergency Off active");
		send(RemoteMessageType.EMERGENCY_PAUSED, lastReceivedSettingsVersion, "");
	}

	public synchronized void resumeParticipant()
	{
		if (role != RemoteRole.PARTICIPANT
			|| snapshot.getState() != RemoteSessionState.EMERGENCY_PAUSED)
		{
			return;
		}
		RemoteTransport relay = relayClient;
		if (relay == null || !relay.isOpen())
		{
			publish(
				RemoteSessionState.EMERGENCY_PAUSED,
				"Cannot resume while the remote relay is disconnected"
			);
			return;
		}
		RemoteSessionState next = effectiveSettings.isRemoteControlled()
			? RemoteSessionState.ACTIVE
			: RemoteSessionState.WAITING_FOR_SETTINGS;
		publish(next, next == RemoteSessionState.ACTIVE
			? "Remote control active"
			: "Waiting for controller settings");
		send(RemoteMessageType.SESSION_RESUMED, lastReceivedSettingsVersion, "");
	}

	public synchronized void endSession()
	{
		endSessionInternal(true, "Remote session ended locally");
	}

	@Override
	public synchronized void close()
	{
		if (closed)
		{
			return;
		}
		closed = true;
		endSessionInternal(false, "Remote service closed");
		scheduler.shutdownNow();
		listeners.clear();
	}

	private void beginSession(RemoteRole nextRole, RemoteInvitation nextInvitation)
	{
		role = nextRole;
		invitation = nextInvitation;
		crypto = new RemoteCrypto(nextInvitation.getKey());
		lastSentSettings = null;
		controllerSettings = null;
		lastHelloNanos = 0;
		nextSettingsVersion = 0;
		lastReceivedSettingsVersion = 0;
		lastAcknowledgedVersion = 0;
		if (nextRole == RemoteRole.PARTICIPANT)
		{
			effectiveSettings.clearRemote();
		}
		publish(RemoteSessionState.CONNECTING, "Connecting to remote relay");

		RemoteTransport client = transportFactory.create(new RelayListener());
		relayClient = client;
		client.connect(nextInvitation.getRelayUrl(), nextInvitation.getRoomId(), nextRole);
	}

	private void tickSafely()
	{
		try
		{
			tick();
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Remote session tick failed", e);
		}
	}

	private synchronized void tick()
	{
		if (closed || role == RemoteRole.NONE || relayClient == null || !relayClient.isOpen())
		{
			return;
		}

		long now = System.nanoTime();
		if (now - lastHelloNanos >= TimeUnit.MILLISECONDS.toNanos(HELLO_INTERVAL_MILLIS)
			&& (snapshot.getState() == RemoteSessionState.WAITING_FOR_PEER
				|| snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS))
		{
			lastHelloNanos = now;
			send(RemoteMessageType.HELLO, 0, role.name());
		}
	}

	void reconcileSettingsSafely()
	{
		try
		{
			synchronized (this)
			{
				if (canSendControllerSettings())
				{
					sendSettings(false);
				}
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Remote settings reconciliation failed", e);
		}
	}

	private void sendDebouncedSettingsSafely()
	{
		try
		{
			synchronized (this)
			{
				pendingSettingsSync = null;
				if (canSendControllerSettings())
				{
					sendSettings(false);
				}
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Immediate remote settings sync failed", e);
		}
	}

	private boolean canSendControllerSettings()
	{
		RemoteTransport relay = relayClient;
		return !closed
			&& role == RemoteRole.CONTROLLER
			&& relay != null
			&& relay.isOpen()
			&& (snapshot.getState() == RemoteSessionState.ACTIVE
				|| snapshot.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED);
	}

	private synchronized void handleOpen()
	{
		if (role == RemoteRole.CONTROLLER)
		{
			publish(RemoteSessionState.WAITING_FOR_PEER, "Waiting for participant");
		}
		else if (role == RemoteRole.PARTICIPANT)
		{
			publish(RemoteSessionState.WAITING_FOR_SETTINGS, "Sending local settings to controller");
		}
		lastHelloNanos = 0;
		send(RemoteMessageType.HELLO, 0, role.name());
		if (role == RemoteRole.PARTICIPANT)
		{
			sendSettingsSeed();
		}
	}

	private synchronized void handleEncryptedMessage(String encrypted)
	{
		RemoteCrypto currentCrypto = crypto;
		if (currentCrypto == null)
		{
			return;
		}

		RemoteProtocolMessage message;
		try
		{
			String plaintext = currentCrypto.decrypt(encrypted);
			message = gson.fromJson(plaintext, RemoteProtocolMessage.class);
			if (message == null || message.getType() == null)
			{
				return;
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignoring invalid remote session message", e);
			return;
		}

		switch (message.getType())
		{
			case HELLO:
				if (role == RemoteRole.CONTROLLER)
				{
					publish(
						RemoteSessionState.WAITING_FOR_SETTINGS,
						"Participant connected. Loading their settings..."
					);
				}
				else if (role == RemoteRole.PARTICIPANT)
				{
					send(RemoteMessageType.HELLO, 0, role.name());
					sendSettingsSeed();
				}
				break;
			case SETTINGS_SEED:
				handleSettingsSeed(message);
				break;
			case SETTINGS:
				handleSettingsMessage(message);
				break;
			case SETTINGS_ACK:
				if (role == RemoteRole.CONTROLLER)
				{
					handleSettingsAcknowledgement(message);
				}
				break;
			case EMERGENCY_PAUSED:
				if (role == RemoteRole.CONTROLLER)
				{
					publish(RemoteSessionState.PEER_EMERGENCY_PAUSED, "Participant used Emergency Off");
				}
				break;
			case SESSION_RESUMED:
				if (role == RemoteRole.CONTROLLER)
				{
					publish(RemoteSessionState.ACTIVE, "Participant resumed remote control");
					sendSettings(true);
				}
				break;
			case SESSION_END:
				endSessionInternal(false, "Remote peer ended the session");
				break;
			default:
				break;
		}
	}

	private void handleSettingsMessage(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT
			|| message.getVersion() < lastReceivedSettingsVersion)
		{
			return;
		}
		if (message.getVersion() == lastReceivedSettingsVersion)
		{
			if (lastReceivedSettingsVersion > 0)
			{
				send(
					RemoteMessageType.SETTINGS_ACK,
					lastReceivedSettingsVersion,
					gson.toJson(settingsStore.capture())
				);
			}
			return;
		}

		try
		{
			RemoteSettingsSnapshot settings = gson.fromJson(
				message.getPayload(),
				RemoteSettingsSnapshot.class
			);
			if (settings == null)
			{
				return;
			}
			settings.validate();
			RemoteSettingsSnapshot canonical = settingsStore.save(settings);
			effectiveSettings.applyRemote(canonical);
			lastReceivedSettingsVersion = message.getVersion();
			for (RemoteSessionListener listener : listeners)
			{
				listener.onRemoteSettingsChanged(canonical);
			}
			if (snapshot.getState() != RemoteSessionState.EMERGENCY_PAUSED)
			{
				publish(RemoteSessionState.ACTIVE, "Remote control active");
			}
			send(
				RemoteMessageType.SETTINGS_ACK,
				lastReceivedSettingsVersion,
				gson.toJson(canonical)
			);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid remote settings snapshot", e);
			emergencyPause();
		}
	}

	private void handleSettingsSeed(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER || controllerSettings != null)
		{
			return;
		}
		try
		{
			RemoteSettingsSnapshot settings = gson.fromJson(
				message.getPayload(),
				RemoteSettingsSnapshot.class
			);
			if (settings == null)
			{
				return;
			}
			settings.validate();
			controllerSettings = settings;
			for (RemoteSessionListener listener : listeners)
			{
				listener.onRemoteSettingsChanged(settings);
			}
			publish(RemoteSessionState.ACTIVE, "Participant settings loaded");
			sendSettings(true);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid participant settings seed", e);
			publish(RemoteSessionState.DISCONNECTED, "Participant settings could not be loaded");
		}
	}

	private void handleSettingsAcknowledgement(RemoteProtocolMessage message)
	{
		long acknowledgedVersion = message.getVersion();
		if (acknowledgedVersion <= lastAcknowledgedVersion
			|| acknowledgedVersion > nextSettingsVersion)
		{
			return;
		}
		try
		{
			RemoteSettingsSnapshot canonical = gson.fromJson(
				message.getPayload(),
				RemoteSettingsSnapshot.class
			);
			if (canonical == null)
			{
				return;
			}
			canonical.validate();
			boolean draftStillMatchesAcknowledgedRequest =
				controllerSettings != null && controllerSettings.equals(lastSentSettings);
			lastAcknowledgedVersion = acknowledgedVersion;
			if (acknowledgedVersion == nextSettingsVersion)
			{
				lastSentSettings = canonical;
				if (draftStillMatchesAcknowledgedRequest)
				{
					controllerSettings = canonical;
					for (RemoteSessionListener listener : listeners)
					{
						listener.onRemoteSettingsChanged(canonical);
					}
				}
			}
			publish(snapshot.getState(), statusForController());
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Ignored invalid settings acknowledgement", e);
		}
	}

	private void sendSettingsSeed()
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		RemoteSettingsSnapshot settings = settingsStore.capture();
		settings.validate();
		send(RemoteMessageType.SETTINGS_SEED, 0, gson.toJson(settings));
	}

	private void sendSettings(boolean force)
	{
		if (role != RemoteRole.CONTROLLER || controllerSettings == null)
		{
			return;
		}
		RemoteSettingsSnapshot settings = controllerSettings;
		settings.validate();
		if (!force && settings.equals(lastSentSettings))
		{
			if (lastAcknowledgedVersion < nextSettingsVersion)
			{
				send(
					RemoteMessageType.SETTINGS,
					nextSettingsVersion,
					gson.toJson(settings)
				);
				publish(snapshot.getState(), statusForController());
			}
			return;
		}
		long version = nextSettingsVersion + 1;
		RemoteSettingsSnapshot previousSent = lastSentSettings;
		long previousVersion = nextSettingsVersion;
		lastSentSettings = settings;
		nextSettingsVersion = version;
		if (!send(RemoteMessageType.SETTINGS, version, gson.toJson(settings)))
		{
			// Preserve a newer update if a synchronous transport callback already
			// advanced the session while this send was in flight.
			if (nextSettingsVersion == version && Objects.equals(lastSentSettings, settings))
			{
				lastSentSettings = previousSent;
				nextSettingsVersion = previousVersion;
			}
		}
		publish(snapshot.getState(), statusForController());
	}

	private String statusForController()
	{
		if (snapshot.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED)
		{
			return "Participant used Emergency Off";
		}
		if (nextSettingsVersion == 0)
		{
			return "Participant connected";
		}
		if (controllerSettings != null && !controllerSettings.equals(lastSentSettings))
		{
			return "Saving changes on participant...";
		}
		if (lastAcknowledgedVersion >= nextSettingsVersion)
		{
			return "Saved on participant";
		}
		return "Saving changes on participant...";
	}

	private boolean send(RemoteMessageType type, long version, String payload)
	{
		RemoteTransport relay = relayClient;
		RemoteCrypto currentCrypto = crypto;
		if (relay == null || currentCrypto == null || !relay.isOpen())
		{
			return false;
		}
		RemoteProtocolMessage message = new RemoteProtocolMessage(type, version, payload);
		try
		{
			return relay.send(currentCrypto.encrypt(gson.toJson(message)));
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Unable to send remote session message", e);
			return false;
		}
	}

	private synchronized void handleConnectionLost(String reason, Throwable error)
	{
		if (closed || role == RemoteRole.NONE)
		{
			return;
		}
		if (error != null)
		{
			LOG.log(Level.WARNING, "Remote relay connection lost: " + reason, error);
		}
		else
		{
			LOG.info("Remote relay connection lost: " + reason);
		}

		if (role == RemoteRole.PARTICIPANT)
		{
			publish(
				RemoteSessionState.EMERGENCY_PAUSED,
				"Remote connection lost. Emergency Off active."
			);
		}
		else
		{
			publish(RemoteSessionState.DISCONNECTED, "Remote connection lost");
		}
	}

	private void publish(RemoteSessionState state, String message)
	{
		long version = role == RemoteRole.CONTROLLER
			? nextSettingsVersion
			: lastReceivedSettingsVersion;
		RemoteSessionSnapshot next = new RemoteSessionSnapshot(role, state, message, version);
		snapshot = next;
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteSessionChanged(next);
		}
	}

	private synchronized void endSessionInternal(boolean notifyPeer, String message)
	{
		if (pendingSettingsSync != null)
		{
			pendingSettingsSync.cancel(false);
			pendingSettingsSync = null;
		}
		if (notifyPeer && role != RemoteRole.NONE)
		{
			send(RemoteMessageType.SESSION_END, 0, "");
		}
		RemoteTransport current = relayClient;
		relayClient = null;
		if (current != null)
		{
			current.close();
		}
		if (role == RemoteRole.PARTICIPANT)
		{
			effectiveSettings.clearRemote();
		}
		role = RemoteRole.NONE;
		crypto = null;
		invitation = null;
		lastSentSettings = null;
		controllerSettings = null;
		lastHelloNanos = 0;
		nextSettingsVersion = 0;
		lastReceivedSettingsVersion = 0;
		lastAcknowledgedVersion = 0;
		snapshot = new RemoteSessionSnapshot(
			RemoteRole.NONE,
			RemoteSessionState.LOCAL,
			message == null ? "Local control" : message,
			0
		);
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteSessionChanged(snapshot);
			listener.onRemoteSettingsChanged(effectiveSettings.current());
		}
	}

	private void requireOpen()
	{
		if (closed)
		{
			throw new IllegalStateException("Remote session service is closed");
		}
	}

	private final class RelayListener implements RemoteTransport.Listener
	{
		@Override
		public void onOpen()
		{
			handleOpen();
		}

		@Override
		public void onMessage(String message)
		{
			handleEncryptedMessage(message);
		}

		@Override
		public void onClosed(String reason)
		{
			handleConnectionLost(reason, null);
		}

		@Override
		public void onFailure(String message, Throwable error)
		{
			handleConnectionLost(message, error);
		}
	}
}
