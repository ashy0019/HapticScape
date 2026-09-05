package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
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
	private static final long HOUSEKEEPING_INTERVAL_MILLIS = 100;
	private static final long SETTINGS_DEBOUNCE_MILLIS = 40;
	private static final long SETTINGS_RECONCILE_INTERVAL_MILLIS = 5_000;
	private static final long HELLO_INTERVAL_MILLIS = 1_000;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final Gson gson;
	private final EffectiveSettingsService effectiveSettings;
	private final SettingsLockService settingsLockService;
	private final RemoteActionCoordinator actionCoordinator;
	private final RemoteLockCoordinator lockCoordinator;
	private final RemotePermissionsCoordinator permissionsCoordinator;
	private final RemoteSettingsCoordinator settingsCoordinator;
	private final RemoteMessageRouter messageRouter;
	private final Clock clock;
	private final RemoteTransportFactory transportFactory;
	private final SettingsLockListener settingsLockListener = this::handleLocalSettingsLockChanged;
	private final SecureRandom random = new SecureRandom();
	private final ScheduledExecutorService scheduler;
	private final CopyOnWriteArrayList<RemoteSessionListener> listeners =
		new CopyOnWriteArrayList<>();

	private volatile RemoteSessionSnapshot snapshot = RemoteSessionSnapshot.local();
	private volatile RemoteTransport relayClient;
	private volatile RemoteRole role = RemoteRole.NONE;
	private volatile RemoteCrypto crypto;
	private volatile RemoteInvitation invitation;
	private volatile long lastHelloNanos;
	private ScheduledFuture<?> pendingSettingsSync;
	private volatile boolean closed;

	public RemoteSessionManager(
		OkHttpClient httpClient,
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings,
		SettingsLockService settingsLockService)
	{
		this(
			httpClient,
			gson,
			settingsStore,
			effectiveSettings,
			settingsLockService,
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP
		);
	}

	public RemoteSessionManager(
		OkHttpClient httpClient,
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings,
		SettingsLockService settingsLockService,
		RemotePermissionsStore permissionsStore,
		RemoteActionExecutor remoteActionExecutor)
	{
		this(
			gson,
			settingsStore,
			effectiveSettings,
			settingsLockService,
			new SavedUnlockKeyStore(gson),
			permissionsStore,
			remoteActionExecutor,
			Clock.systemUTC(),
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
		SettingsLockService settingsLockService,
		RemoteTransportFactory transportFactory)
	{
		this(
			gson,
			settingsStore,
			effectiveSettings,
			settingsLockService,
			SavedUnlockKeyStore.disabled(gson),
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			Clock.systemUTC(),
			transportFactory
		);
	}

	RemoteSessionManager(
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings,
		SettingsLockService settingsLockService,
		SavedUnlockKeyStore savedUnlockKeyStore,
		RemoteTransportFactory transportFactory)
	{
		this(
			gson,
			settingsStore,
			effectiveSettings,
			settingsLockService,
			savedUnlockKeyStore,
			new InMemoryRemotePermissionsStore(RemotePermissions.defaults()),
			RemoteActionExecutor.NO_OP,
			Clock.systemUTC(),
			transportFactory
		);
	}

	RemoteSessionManager(
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings,
		SettingsLockService settingsLockService,
		SavedUnlockKeyStore savedUnlockKeyStore,
		RemotePermissionsStore permissionsStore,
		RemoteActionExecutor remoteActionExecutor,
		Clock clock,
		RemoteTransportFactory transportFactory)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		RemoteSettingsStore requiredSettingsStore = Objects.requireNonNull(
			settingsStore,
			"settingsStore"
		);
		this.effectiveSettings = Objects.requireNonNull(effectiveSettings, "effectiveSettings");
		this.settingsLockService = Objects.requireNonNull(settingsLockService, "settingsLockService");
		SavedUnlockKeyStore requiredSavedUnlockKeyStore = Objects.requireNonNull(
			savedUnlockKeyStore,
			"savedUnlockKeyStore"
		);
		RemotePermissionsStore requiredPermissionsStore = Objects.requireNonNull(
			permissionsStore,
			"permissionsStore"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
		RemoteActionExecutor actionExecutor = Objects.requireNonNull(
			remoteActionExecutor,
			"remoteActionExecutor"
		);
		this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
		this.actionCoordinator = new RemoteActionCoordinator(
			gson,
			actionExecutor,
			clock,
			this::send,
			this::publishActionAcknowledgement
		);
		this.lockCoordinator = new RemoteLockCoordinator(
			gson,
			settingsLockService,
			requiredSavedUnlockKeyStore,
			this::send,
			this::publishLockSnapshot,
			this::publishLockProposal
		);
		this.permissionsCoordinator = new RemotePermissionsCoordinator(
			gson,
			requiredPermissionsStore,
			this::send,
			this::publishPermissions,
			actionCoordinator::clearControllerLiveStream
		);
		this.settingsCoordinator = new RemoteSettingsCoordinator(
			gson,
			requiredSettingsStore,
			effectiveSettings,
			this::send,
			this::publish,
			this::publishSettings,
			this::emergencyPause
		);
		this.messageRouter = new RemoteMessageRouter(
			settingsCoordinator,
			permissionsCoordinator,
			lockCoordinator,
			actionCoordinator,
			new LifecycleMessages()
		);
		this.settingsLockService.addListener(settingsLockListener);
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

	public RemoteLockSnapshot getLockSnapshot()
	{
		return lockCoordinator.getSnapshot();
	}

	/** Returns the controller's current participant draft, if one has loaded. */
	public RemoteSettingsSnapshot getControllerSettingsSnapshot()
	{
		return settingsCoordinator.getControllerSettings();
	}

	/** Participant-owned local permissions, or the controller's read-only peer view. */
	public RemotePermissions getVisiblePermissions()
	{
		return visiblePermissions();
	}

	public RemotePermissions getPeerPermissions()
	{
		return permissionsCoordinator.getPeer();
	}

	public synchronized RemotePermissions updateLocalPermissions(RemotePermissions permissions)
	{
		RemotePermissions saved = permissionsCoordinator.updateLocal(role, permissions);
		actionCoordinator.permissionsChanged(
			saved,
			snapshot.getState() != RemoteSessionState.ACTIVE
		);
		return saved;
	}

	public synchronized String sendRemoteHaptic(
		String patternSelection,
		int intensityPercent,
		int durationMillis)
	{
		return actionCoordinator.sendHaptic(
			role,
			snapshot.getState(),
			patternSelection,
			intensityPercent,
			durationMillis
		);
	}

	public synchronized String sendRemoteClick()
	{
		return actionCoordinator.sendClick(role, snapshot.getState());
	}

	public synchronized String sendRemoteMessage(
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		return actionCoordinator.sendMessage(
			role,
			snapshot.getState(),
			message,
			desktopNotification,
			localChatboxMessage
		);
	}

	public synchronized String stopRemoteOutput()
	{
		return actionCoordinator.stop(role, snapshot.getState());
	}

	public synchronized void beginRemoteLiveHaptic(int intensityPercent)
	{
		actionCoordinator.beginLive(
			role,
			snapshot.getState(),
			permissionsCoordinator.getPeer(),
			intensityPercent
		);
	}

	public synchronized void updateRemoteLiveHaptic(int intensityPercent)
	{
		actionCoordinator.updateLive(
			role,
			snapshot.getState(),
			permissionsCoordinator.getPeer(),
			intensityPercent
		);
	}

	public synchronized void endRemoteLiveHaptic()
	{
		actionCoordinator.endLive(role, snapshot.getState());
	}

	public void addListener(RemoteSessionListener listener)
	{
		RemoteSessionListener required = Objects.requireNonNull(listener, "listener");
		listeners.add(required);
		required.onRemoteSessionChanged(snapshot);
		required.onRemoteLockChanged(lockCoordinator.getSnapshot());
		required.onRemotePermissionsChanged(visiblePermissions());
	}

	public void removeListener(RemoteSessionListener listener)
	{
		listeners.remove(listener);
	}

	public synchronized boolean updateControllerSetting(String key, Object value)
	{
		boolean updated = settingsCoordinator.updateControllerSetting(
			role,
			snapshot.getState(),
			permissionsCoordinator.getPeer(),
			key,
			value
		);
		if (!updated)
		{
			return false;
		}

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

	public char[] generateSettingsLockKey()
	{
		return lockCoordinator.generateUnlockKey();
	}

	public List<SavedUnlockKey> getSavedUnlockKeys()
	{
		return lockCoordinator.getSavedUnlockKeys();
	}

	public boolean isSavedUnlockKeyVaultAvailable()
	{
		return lockCoordinator.isSavedUnlockKeyVaultAvailable();
	}

	public String getSavedUnlockKeyVaultMessage()
	{
		return lockCoordinator.getSavedUnlockKeyVaultMessage();
	}

	public char[] revealSavedUnlockKey(String id)
	{
		return lockCoordinator.revealSavedUnlockKey(id);
	}

	public SavedUnlockKey updateSavedUnlockKey(
		String id,
		String label,
		String note)
	{
		return lockCoordinator.updateSavedUnlockKey(id, label, note);
	}

	public boolean forgetSavedUnlockKey(String id)
	{
		return lockCoordinator.forgetSavedUnlockKey(id);
	}

	public synchronized void proposeSettingsLock(char[] password)
	{
		lockCoordinator.propose(role, snapshot.getState(), password);
	}

	public synchronized void cancelSettingsLock()
	{
		lockCoordinator.cancel(role);
	}

	public synchronized void acceptPendingSettingsLock()
	{
		lockCoordinator.accept(role);
	}

	public synchronized void declinePendingSettingsLock()
	{
		lockCoordinator.decline(role);
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
		if (settingsLockService.isLocked())
		{
			throw new IllegalStateException(
				"Unlock local feedback settings before joining another Remote Control session"
			);
		}
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
		actionCoordinator.stopParticipantOutput();
		publish(RemoteSessionState.EMERGENCY_PAUSED, "Emergency Off active");
		send(
			RemoteMessageType.EMERGENCY_PAUSED,
			settingsCoordinator.getLastReceivedVersion(),
			""
		);
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
		send(
			RemoteMessageType.SESSION_RESUMED,
			settingsCoordinator.getLastReceivedVersion(),
			""
		);
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
		settingsLockService.removeListener(settingsLockListener);
	}

	private void beginSession(RemoteRole nextRole, RemoteInvitation nextInvitation)
	{
		role = nextRole;
		permissionsCoordinator.beginSession();
		actionCoordinator.reset();
		invitation = nextInvitation;
		crypto = new RemoteCrypto(nextInvitation.getKey());
		lastHelloNanos = 0;
		settingsCoordinator.reset();
		lockCoordinator.reset();
		if (nextRole == RemoteRole.PARTICIPANT)
		{
			effectiveSettings.clearRemote();
		}
		publish(RemoteSessionState.CONNECTING, "Connecting to remote relay");
		publishPermissions(visiblePermissions());

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
		boolean activeTransport = !closed
			&& role != RemoteRole.NONE
			&& relayClient != null
			&& relayClient.isOpen();
		actionCoordinator.tick(
			role,
			snapshot.getState(),
			permissionsCoordinator.getLocal(),
			activeTransport
		);
		if (!activeTransport)
		{
			return;
		}

		long now = System.nanoTime();
		if (now - lastHelloNanos >= TimeUnit.MILLISECONDS.toNanos(HELLO_INTERVAL_MILLIS)
			&& (snapshot.getState() == RemoteSessionState.WAITING_FOR_PEER
				|| snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS))
		{
			lastHelloNanos = now;
			retryHandshake();
		}
		lockCoordinator.tick(role, snapshot.getState(), now);
	}

	/** Retries every idempotent frame needed to finish the initial handshake. */
	void retryHandshakeSafely()
	{
		try
		{
			synchronized (this)
			{
				if (!closed && relayClient != null && relayClient.isOpen())
				{
					retryHandshake();
				}
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Remote handshake retry failed", e);
		}
	}

	private void retryHandshake()
	{
		send(RemoteMessageType.HELLO, 0, role.name());
		if (role == RemoteRole.CONTROLLER
			&& snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS)
		{
			send(RemoteMessageType.SETTINGS_SEED_REQUEST, 0, "");
		}
		else if (role == RemoteRole.PARTICIPANT
			&& snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS)
		{
			sendPermissions();
			sendSettingsSeed();
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
		return settingsCoordinator.canSendControllerSettings(
			closed,
			relay != null && relay.isOpen(),
			role,
			snapshot.getState(),
			permissionsCoordinator.getPeer()
		);
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
		retryHandshake();
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

		messageRouter.route(role, snapshot.getState(), message);
	}

	private void sendSettingsSeed()
	{
		settingsCoordinator.sendSeed(role);
	}

	private void sendPermissions()
	{
		permissionsCoordinator.send(role);
	}

	private void sendSettings(boolean force)
	{
		settingsCoordinator.sendSettings(
			force,
			role,
			snapshot.getState(),
			permissionsCoordinator.getPeer()
		);
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
			actionCoordinator.stopParticipantOutput();
			publish(
				RemoteSessionState.EMERGENCY_PAUSED,
				"Remote connection lost. Emergency Off active."
			);
		}
		else
		{
			actionCoordinator.clearControllerLiveStream();
			lockCoordinator.handleControllerConnectionLost();
			publish(RemoteSessionState.DISCONNECTED, "Remote connection lost");
		}
	}

	private void publish(RemoteSessionState state, String message)
	{
		long version = settingsCoordinator.sessionVersion(role);
		RemoteSessionSnapshot next = new RemoteSessionSnapshot(role, state, message, version);
		snapshot = next;
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteSessionChanged(next);
		}
	}

	private void publishLockSnapshot(RemoteLockSnapshot next)
	{
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteLockChanged(next);
		}
	}

	private void publishLockProposal(SettingsLockProposal proposal)
	{
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteLockProposal(proposal);
		}
	}

	private void publishSettings(RemoteSettingsSnapshot settings)
	{
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteSettingsChanged(settings);
		}
	}

	private void publishActionAcknowledgement(
		RemoteActionAcknowledgement acknowledgement)
	{
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteActionAcknowledged(acknowledgement);
		}
	}

	private RemotePermissions visiblePermissions()
	{
		return permissionsCoordinator.getVisible(role);
	}

	private void publishPermissions(RemotePermissions permissions)
	{
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemotePermissionsChanged(permissions);
		}
	}

	private synchronized void handleLocalSettingsLockChanged(boolean locked)
	{
		lockCoordinator.handleLocalSettingsLockChanged(role, locked);
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
			actionCoordinator.stopParticipantOutput();
			effectiveSettings.clearRemote();
		}
		role = RemoteRole.NONE;
		actionCoordinator.reset();
		crypto = null;
		invitation = null;
		settingsCoordinator.reset();
		permissionsCoordinator.endSession();
		lastHelloNanos = 0;
		lockCoordinator.reset();
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
			listener.onRemoteLockChanged(lockCoordinator.getSnapshot());
			listener.onRemotePermissionsChanged(permissionsCoordinator.getLocal());
		}
	}

	private void requireOpen()
	{
		if (closed)
		{
			throw new IllegalStateException("Remote session service is closed");
		}
	}

	private final class LifecycleMessages implements RemoteLifecycleMessageHandler
	{
		@Override
		public void handleHello()
		{
			if (role == RemoteRole.CONTROLLER)
			{
				if (!settingsCoordinator.hasControllerSettings())
				{
					publish(
						RemoteSessionState.WAITING_FOR_SETTINGS,
						"Participant connected. Loading their settings..."
					);
				}
				send(RemoteMessageType.SETTINGS_SEED_REQUEST, 0, "");
			}
			else if (role == RemoteRole.PARTICIPANT)
			{
				send(RemoteMessageType.HELLO, 0, role.name());
				sendPermissions();
				sendSettingsSeed();
			}
		}

		@Override
		public void handleSettingsSeedRequest()
		{
			if (role == RemoteRole.PARTICIPANT)
			{
				sendPermissions();
				sendSettingsSeed();
			}
		}

		@Override
		public void handlePeerEmergencyPause()
		{
			if (role == RemoteRole.CONTROLLER)
			{
				actionCoordinator.clearControllerLiveStream();
				publish(
					RemoteSessionState.PEER_EMERGENCY_PAUSED,
					"Participant used Emergency Off"
				);
			}
		}

		@Override
		public void handlePeerResume()
		{
			if (role != RemoteRole.CONTROLLER)
			{
				return;
			}
			if (!settingsCoordinator.hasControllerSettings())
			{
				publish(
					RemoteSessionState.WAITING_FOR_SETTINGS,
					"Participant resumed. Loading their settings..."
				);
				send(RemoteMessageType.SETTINGS_SEED_REQUEST, 0, "");
			}
			else
			{
				publish(RemoteSessionState.ACTIVE, "Participant resumed remote control");
				sendSettings(true);
			}
		}

		@Override
		public void handlePeerEnd()
		{
			endSessionInternal(false, "Remote peer ended the session");
		}

		@Override
		public void publishStatus(String message)
		{
			publish(snapshot.getState(), message);
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
