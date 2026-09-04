package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
	private static final long LOCK_PROPOSAL_RETRY_MILLIS = 2_000;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final Gson gson;
	private final RemoteSettingsStore settingsStore;
	private final EffectiveSettingsService effectiveSettings;
	private final SettingsLockService settingsLockService;
	private final SavedUnlockKeyStore savedUnlockKeyStore;
	private final RemotePermissionsStore permissionsStore;
	private final RemoteActionService remoteActionService;
	private final Clock clock;
	private final RemoteTransportFactory transportFactory;
	private final SettingsLockListener settingsLockListener = this::handleLocalSettingsLockChanged;
	private final SecureRandom random = new SecureRandom();
	private final ScheduledExecutorService scheduler;
	private final CopyOnWriteArrayList<RemoteSessionListener> listeners =
		new CopyOnWriteArrayList<>();
	private final Map<String, Long> pendingRemoteActions = new LinkedHashMap<>();

	private volatile RemoteSessionSnapshot snapshot = RemoteSessionSnapshot.local();
	private volatile RemoteTransport relayClient;
	private volatile RemoteRole role = RemoteRole.NONE;
	private volatile RemoteCrypto crypto;
	private volatile RemoteInvitation invitation;
	private volatile RemoteSettingsSnapshot lastSentSettings;
	private volatile RemoteSettingsSnapshot controllerSettings;
	private volatile RemotePermissions localPermissions;
	private volatile RemotePermissions peerPermissions = RemotePermissions.none();
	private volatile long lastHelloNanos;
	private volatile long nextSettingsVersion;
	private volatile long lastReceivedSettingsVersion;
	private volatile long lastAcknowledgedVersion;
	private volatile RemoteLockSnapshot lockSnapshot = RemoteLockSnapshot.inactive();
	private volatile SettingsLockProposal controllerLockProposal;
	private char[] pendingControllerUnlockKey;
	private volatile SettingsLockProposal participantLockProposal;
	private volatile String participantArmedLockId;
	private volatile String participantDeclinedLockId;
	private volatile long lastLockProposalNanos;
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
		this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
		this.effectiveSettings = Objects.requireNonNull(effectiveSettings, "effectiveSettings");
		this.settingsLockService = Objects.requireNonNull(settingsLockService, "settingsLockService");
		this.savedUnlockKeyStore = Objects.requireNonNull(
			savedUnlockKeyStore,
			"savedUnlockKeyStore"
		);
		this.permissionsStore = Objects.requireNonNull(permissionsStore, "permissionsStore");
		this.localPermissions = permissionsStore.capture();
		this.localPermissions.validate();
		this.clock = Objects.requireNonNull(clock, "clock");
		this.remoteActionService = new RemoteActionService(remoteActionExecutor, clock);
		this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
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
		return lockSnapshot;
	}

	/** Returns the controller's current participant draft, if one has loaded. */
	public RemoteSettingsSnapshot getControllerSettingsSnapshot()
	{
		return controllerSettings;
	}

	/** Participant-owned local permissions, or the controller's read-only peer view. */
	public RemotePermissions getVisiblePermissions()
	{
		return visiblePermissions();
	}

	public RemotePermissions getPeerPermissions()
	{
		return peerPermissions;
	}

	public synchronized RemotePermissions updateLocalPermissions(RemotePermissions permissions)
	{
		if (role == RemoteRole.CONTROLLER)
		{
			throw new IllegalStateException("A controller cannot change participant permissions");
		}
		RemotePermissions saved = permissionsStore.save(
			Objects.requireNonNull(permissions, "permissions")
		);
		saved.validate();
		localPermissions = saved;
		publishPermissions(saved);
		if (role == RemoteRole.PARTICIPANT)
		{
			sendPermissions();
		}
		return saved;
	}

	public synchronized String sendRemoteHaptic(
		String patternSelection,
		int intensityPercent,
		int durationMillis)
	{
		return sendRemoteAction(RemoteAction.haptic(
			patternSelection,
			intensityPercent,
			durationMillis,
			clock
		));
	}

	public synchronized String sendRemoteClick()
	{
		return sendRemoteAction(RemoteAction.click(clock));
	}

	public synchronized String sendRemoteMessage(
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		return sendRemoteAction(RemoteAction.message(
			message,
			desktopNotification,
			localChatboxMessage,
			clock
		));
	}

	public synchronized String stopRemoteOutput()
	{
		return sendRemoteAction(RemoteAction.stop(clock));
	}

	public void addListener(RemoteSessionListener listener)
	{
		RemoteSessionListener required = Objects.requireNonNull(listener, "listener");
		listeners.add(required);
		required.onRemoteSessionChanged(snapshot);
		required.onRemoteLockChanged(lockSnapshot);
		required.onRemotePermissionsChanged(visiblePermissions());
	}

	public void removeListener(RemoteSessionListener listener)
	{
		listeners.remove(listener);
	}

	public synchronized boolean updateControllerSetting(String key, Object value)
	{
		if (role != RemoteRole.CONTROLLER
			|| controllerSettings == null
			|| !peerPermissions.isSettingsAllowed()
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

	private String sendRemoteAction(RemoteAction action)
	{
		if (role != RemoteRole.CONTROLLER
			|| (snapshot.getState() != RemoteSessionState.ACTIVE
				&& !(action.getType() == RemoteActionType.STOP
					&& snapshot.getState() == RemoteSessionState.PEER_EMERGENCY_PAUSED)))
		{
			throw new IllegalStateException("A participant must be connected and active");
		}
		action.validate();
		pendingRemoteActions.put(action.getActionId(), action.getExpiresAtEpochMillis());
		if (!send(RemoteMessageType.REMOTE_ACTION, 0, gson.toJson(action)))
		{
			pendingRemoteActions.remove(action.getActionId());
			throw new IllegalStateException("Remote action could not be sent");
		}
		return action.getActionId();
	}

	public boolean isControllerSession()
	{
		return role == RemoteRole.CONTROLLER
			&& snapshot.getState() != RemoteSessionState.LOCAL;
	}

	public char[] generateSettingsLockKey()
	{
		return settingsLockService.generateUnlockKey();
	}

	public List<SavedUnlockKey> getSavedUnlockKeys()
	{
		return savedUnlockKeyStore.list();
	}

	public boolean isSavedUnlockKeyVaultAvailable()
	{
		return savedUnlockKeyStore.isAvailable();
	}

	public String getSavedUnlockKeyVaultMessage()
	{
		return savedUnlockKeyStore.getUnavailableMessage();
	}

	public char[] revealSavedUnlockKey(String id)
	{
		return savedUnlockKeyStore.reveal(id);
	}

	public SavedUnlockKey updateSavedUnlockKey(
		String id,
		String label,
		String note)
	{
		return savedUnlockKeyStore.updateDetails(id, label, note);
	}

	public boolean forgetSavedUnlockKey(String id)
	{
		return savedUnlockKeyStore.forget(id);
	}

	public synchronized void proposeSettingsLock(char[] password)
	{
		if (role != RemoteRole.CONTROLLER
			|| (snapshot.getState() != RemoteSessionState.ACTIVE
				&& snapshot.getState() != RemoteSessionState.PEER_EMERGENCY_PAUSED))
		{
			throw new IllegalStateException("A participant must be connected first");
		}
		if (lockSnapshot.getState() == RemoteLockState.AWAITING_APPROVAL
			|| lockSnapshot.getState() == RemoteLockState.ARMED)
		{
			throw new IllegalStateException(
				"Cancel the current post-session lock before creating another"
			);
		}
		SettingsLockProposal proposal = settingsLockService.createProposal(password);
		char[] pendingKey = Arrays.copyOf(password, password.length);
		clearPendingControllerUnlockKey();
		controllerLockProposal = proposal;
		pendingControllerUnlockKey = pendingKey;
		lastLockProposalNanos = 0;
		publishLock(
			RemoteLockState.AWAITING_APPROVAL,
			"Waiting for participant approval"
		);
		sendControllerLockProposal();
	}

	public synchronized void cancelSettingsLock()
	{
		if (role != RemoteRole.CONTROLLER || controllerLockProposal == null)
		{
			return;
		}
		if (lockSnapshot.getState() == RemoteLockState.DECLINED)
		{
			clearPendingControllerUnlockKey();
			controllerLockProposal = null;
			publishLock(RemoteLockState.INACTIVE, "No post-session lock requested");
			return;
		}
		send(
			RemoteMessageType.LOCK_CANCEL_REQUEST,
			0,
			controllerLockProposal.getProposalId()
		);
	}

	public synchronized void acceptPendingSettingsLock()
	{
		if (role != RemoteRole.PARTICIPANT || participantLockProposal == null)
		{
			return;
		}
		SettingsLockProposal proposal = participantLockProposal;
		try
		{
			participantArmedLockId = proposal.getProposalId();
			settingsLockService.arm(proposal);
			participantLockProposal = null;
			participantDeclinedLockId = null;
			publishLock(RemoteLockState.ARMED, "Post-session settings lock armed");
			send(RemoteMessageType.LOCK_ACCEPTED, 0, participantArmedLockId);
		}
		catch (RuntimeException e)
		{
			participantArmedLockId = null;
			participantLockProposal = null;
			participantDeclinedLockId = proposal.getProposalId();
			publishLock(RemoteLockState.DECLINED, "Settings lock could not be saved");
			send(RemoteMessageType.LOCK_DECLINED, 0, participantDeclinedLockId);
			LOG.log(Level.WARNING, "Unable to arm participant settings lock", e);
		}
	}

	public synchronized void declinePendingSettingsLock()
	{
		if (role != RemoteRole.PARTICIPANT || participantLockProposal == null)
		{
			return;
		}
		participantDeclinedLockId = participantLockProposal.getProposalId();
		participantLockProposal = null;
		publishLock(RemoteLockState.DECLINED, "Post-session lock declined");
		send(RemoteMessageType.LOCK_DECLINED, 0, participantDeclinedLockId);
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
		remoteActionService.stopOutputSafely();
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
		settingsLockService.removeListener(settingsLockListener);
	}

	private void beginSession(RemoteRole nextRole, RemoteInvitation nextInvitation)
	{
		role = nextRole;
		localPermissions = permissionsStore.capture();
		localPermissions.validate();
		peerPermissions = RemotePermissions.none();
		remoteActionService.reset();
		invitation = nextInvitation;
		crypto = new RemoteCrypto(nextInvitation.getKey());
		lastSentSettings = null;
		controllerSettings = null;
		lastHelloNanos = 0;
		nextSettingsVersion = 0;
		lastReceivedSettingsVersion = 0;
		lastAcknowledgedVersion = 0;
		controllerLockProposal = null;
		participantLockProposal = null;
		participantArmedLockId = null;
		participantDeclinedLockId = null;
		lastLockProposalNanos = 0;
		lockSnapshot = RemoteLockSnapshot.inactive();
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
		if (closed || role == RemoteRole.NONE || relayClient == null || !relayClient.isOpen())
		{
			return;
		}

		long now = System.nanoTime();
		pendingRemoteActions.entrySet().removeIf(
			entry -> clock.millis() > entry.getValue() + 5_000
		);
		if (now - lastHelloNanos >= TimeUnit.MILLISECONDS.toNanos(HELLO_INTERVAL_MILLIS)
			&& (snapshot.getState() == RemoteSessionState.WAITING_FOR_PEER
				|| snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS))
		{
			lastHelloNanos = now;
			retryHandshake();
		}
		if (role == RemoteRole.CONTROLLER
			&& controllerLockProposal != null
			&& lockSnapshot.getState() == RemoteLockState.AWAITING_APPROVAL
			&& now - lastLockProposalNanos >= TimeUnit.MILLISECONDS.toNanos(
				LOCK_PROPOSAL_RETRY_MILLIS
			))
		{
			sendControllerLockProposal();
		}
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
		return !closed
			&& role == RemoteRole.CONTROLLER
			&& peerPermissions.isSettingsAllowed()
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

		switch (message.getType())
		{
			case HELLO:
				if (role == RemoteRole.CONTROLLER)
				{
					if (controllerSettings == null)
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
				break;
			case SETTINGS_SEED_REQUEST:
				if (role == RemoteRole.PARTICIPANT)
				{
					sendPermissions();
					sendSettingsSeed();
				}
				break;
			case PERMISSIONS:
				handlePermissions(message);
				break;
			case SETTINGS_SEED:
				handleSettingsSeed(message);
				break;
			case SETTINGS_SEED_ACK:
				if (role == RemoteRole.PARTICIPANT
					&& snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS)
				{
					publish(
						RemoteSessionState.ACTIVE,
						localPermissions.isSettingsAllowed()
							? "Remote control active"
							: "Remote actions active; settings changes disabled"
					);
				}
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
			case SETTINGS_REJECTED:
				if (role == RemoteRole.CONTROLLER)
				{
					publish(snapshot.getState(), "Participant disabled remote settings");
				}
				break;
			case REMOTE_ACTION:
				handleRemoteAction(message);
				break;
			case REMOTE_ACTION_ACK:
				handleRemoteActionAcknowledgement(message);
				break;
			case LOCK_PROPOSAL:
				handleLockProposal(message);
				break;
			case LOCK_ACCEPTED:
				handleLockAccepted(message);
				break;
			case LOCK_DECLINED:
				handleLockDeclined(message);
				break;
			case LOCK_CANCEL_REQUEST:
				handleLockCancelRequest(message);
				break;
			case LOCK_CANCELLED:
				handleLockCancelled(message);
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
					if (controllerSettings == null)
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
		if (!localPermissions.isSettingsAllowed())
		{
			send(
				RemoteMessageType.SETTINGS_REJECTED,
				message.getVersion(),
				"Participant disabled remote settings"
			);
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
		if (role != RemoteRole.CONTROLLER)
		{
			return;
		}
		if (controllerSettings != null)
		{
			// The first acknowledgement may have been lost. Acknowledge duplicate
			// seeds so the participant can leave its guarded waiting state.
			if (snapshot.getState() == RemoteSessionState.WAITING_FOR_SETTINGS)
			{
				publish(RemoteSessionState.ACTIVE, "Participant settings loaded");
			}
			send(RemoteMessageType.SETTINGS_SEED_ACK, 0, "");
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
			send(RemoteMessageType.SETTINGS_SEED_ACK, 0, "");
			if (peerPermissions.isSettingsAllowed())
			{
				sendSettings(true);
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid participant settings seed", e);
			publish(RemoteSessionState.DISCONNECTED, "Participant settings could not be loaded");
		}
	}

	private void handlePermissions(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER)
		{
			return;
		}
		try
		{
			RemotePermissions permissions = gson.fromJson(
				message.getPayload(),
				RemotePermissions.class
			);
			if (permissions == null)
			{
				return;
			}
			permissions.validate();
			peerPermissions = permissions;
			publishPermissions(permissions);
			if (!permissions.isSettingsAllowed())
			{
				publish(snapshot.getState(), "Participant disabled remote settings");
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored invalid remote permissions", e);
		}
	}

	private void handleRemoteAction(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		RemoteAction action;
		try
		{
			action = gson.fromJson(message.getPayload(), RemoteAction.class);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored malformed remote action", e);
			return;
		}
		RemoteActionAcknowledgement acknowledgement = remoteActionService.process(
			action,
			localPermissions,
			snapshot.getState() != RemoteSessionState.ACTIVE
		);
		if (acknowledgement != null)
		{
			send(RemoteMessageType.REMOTE_ACTION_ACK, 0, gson.toJson(acknowledgement));
		}
	}

	private void handleRemoteActionAcknowledgement(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER)
		{
			return;
		}
		try
		{
			RemoteActionAcknowledgement acknowledgement = gson.fromJson(
				message.getPayload(),
				RemoteActionAcknowledgement.class
			);
			if (acknowledgement == null)
			{
				return;
			}
			acknowledgement.validate();
			if (pendingRemoteActions.remove(acknowledgement.getActionId()) == null)
			{
				return;
			}
			for (RemoteSessionListener listener : listeners)
			{
				listener.onRemoteActionAcknowledged(acknowledgement);
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored invalid remote action acknowledgement", e);
		}
	}

	private void handleLockProposal(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		try
		{
			SettingsLockProposal proposal = gson.fromJson(
				message.getPayload(),
				SettingsLockProposal.class
			);
			if (proposal == null)
			{
				return;
			}
			proposal.validate();
			String proposalId = proposal.getProposalId();
			if (proposalId.equals(participantArmedLockId))
			{
				send(RemoteMessageType.LOCK_ACCEPTED, 0, proposalId);
				return;
			}
			if (proposalId.equals(participantDeclinedLockId))
			{
				send(RemoteMessageType.LOCK_DECLINED, 0, proposalId);
				return;
			}
			if (participantLockProposal != null
				&& proposalId.equals(participantLockProposal.getProposalId()))
			{
				return;
			}
			if (settingsLockService.isLocked())
			{
				participantDeclinedLockId = proposalId;
				publishLock(RemoteLockState.DECLINED, "Settings are already locked");
				send(RemoteMessageType.LOCK_DECLINED, 0, proposalId);
				return;
			}
			participantLockProposal = proposal;
			publishLock(
				RemoteLockState.APPROVAL_REQUIRED,
				"Controller requests a post-session settings lock"
			);
			for (RemoteSessionListener listener : listeners)
			{
				listener.onRemoteLockProposal(proposal);
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid settings-lock proposal", e);
		}
	}

	private void handleLockAccepted(RemoteProtocolMessage message)
	{
		if (role == RemoteRole.CONTROLLER
			&& matchesControllerLockProposal(message.getPayload()))
		{
			String status = "Participant accepted; settings lock armed";
			try
			{
				if (pendingControllerUnlockKey == null)
				{
					status = savedUnlockKeyStore.findByLockId(message.getPayload()).isPresent()
						? "Participant accepted; unlock key saved"
						: "Participant accepted; unlock key is not available";
				}
				else if (savedUnlockKeyStore.isAvailable())
				{
					savedUnlockKeyStore.saveAcceptedKey(
						message.getPayload(),
						pendingControllerUnlockKey
					);
					status = "Participant accepted; unlock key saved";
				}
				else
				{
					status = "Participant accepted; secure key vault unavailable";
				}
			}
			catch (RuntimeException e)
			{
				status = "Participant accepted; unlock key could not be saved";
				LOG.log(Level.WARNING, "Unable to save accepted unlock key", e);
			}
			finally
			{
				clearPendingControllerUnlockKey();
			}
			publishLock(RemoteLockState.ARMED, status);
		}
	}

	private void handleLockDeclined(RemoteProtocolMessage message)
	{
		if (role == RemoteRole.CONTROLLER
			&& matchesControllerLockProposal(message.getPayload()))
		{
			clearPendingControllerUnlockKey();
			publishLock(RemoteLockState.DECLINED, "Participant declined the settings lock");
		}
	}

	private void handleLockCancelRequest(RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		String proposalId = message.getPayload();
		boolean pendingMatch = participantLockProposal != null
			&& participantLockProposal.getProposalId().equals(proposalId);
		boolean armedMatch = proposalId != null && proposalId.equals(participantArmedLockId);
		if (!pendingMatch && !armedMatch)
		{
			return;
		}
		participantLockProposal = null;
		participantDeclinedLockId = null;
		if (armedMatch)
		{
			participantArmedLockId = null;
			settingsLockService.clearAllLocks();
		}
		publishLock(RemoteLockState.INACTIVE, "Post-session settings lock cancelled");
		send(RemoteMessageType.LOCK_CANCELLED, 0, proposalId);
	}

	private void handleLockCancelled(RemoteProtocolMessage message)
	{
		if (role == RemoteRole.CONTROLLER
			&& matchesControllerLockProposal(message.getPayload()))
		{
			String lockId = controllerLockProposal.getProposalId();
			clearPendingControllerUnlockKey();
			controllerLockProposal = null;
			String status = "Post-session settings lock cancelled";
			try
			{
				savedUnlockKeyStore.forgetByLockId(lockId);
			}
			catch (RuntimeException e)
			{
				status = "Settings lock cancelled; saved key could not be removed";
				LOG.log(Level.WARNING, "Unable to remove cancelled unlock key", e);
			}
			publishLock(RemoteLockState.INACTIVE, status);
		}
	}

	private boolean matchesControllerLockProposal(String proposalId)
	{
		return controllerLockProposal != null
			&& controllerLockProposal.getProposalId().equals(proposalId);
	}

	private void sendControllerLockProposal()
	{
		SettingsLockProposal proposal = controllerLockProposal;
		if (proposal == null)
		{
			return;
		}
		lastLockProposalNanos = System.nanoTime();
		send(RemoteMessageType.LOCK_PROPOSAL, 0, gson.toJson(proposal));
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

	private void sendPermissions()
	{
		if (role == RemoteRole.PARTICIPANT)
		{
			send(RemoteMessageType.PERMISSIONS, 0, gson.toJson(localPermissions));
		}
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
		if (!peerPermissions.isSettingsAllowed())
		{
			return "Participant disabled remote settings";
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
			remoteActionService.stopOutputSafely();
			publish(
				RemoteSessionState.EMERGENCY_PAUSED,
				"Remote connection lost. Emergency Off active."
			);
		}
		else
		{
			clearPendingControllerUnlockKey();
			controllerLockProposal = null;
			publishLock(RemoteLockState.INACTIVE, "Pending unlock key discarded");
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

	private void publishLock(RemoteLockState state, String message)
	{
		RemoteLockSnapshot next = new RemoteLockSnapshot(state, message);
		lockSnapshot = next;
		for (RemoteSessionListener listener : listeners)
		{
			listener.onRemoteLockChanged(next);
		}
	}

	private RemotePermissions visiblePermissions()
	{
		return role == RemoteRole.CONTROLLER ? peerPermissions : localPermissions;
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
		if (locked || role != RemoteRole.PARTICIPANT || participantArmedLockId == null)
		{
			return;
		}
		String clearedId = participantArmedLockId;
		participantArmedLockId = null;
		publishLock(RemoteLockState.INACTIVE, "Settings lock cleared locally");
		send(RemoteMessageType.LOCK_CANCELLED, 0, clearedId);
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
			remoteActionService.stopOutputSafely();
			effectiveSettings.clearRemote();
		}
		role = RemoteRole.NONE;
		remoteActionService.reset();
		pendingRemoteActions.clear();
		crypto = null;
		invitation = null;
		lastSentSettings = null;
		controllerSettings = null;
		peerPermissions = RemotePermissions.none();
		localPermissions = permissionsStore.capture();
		lastHelloNanos = 0;
		nextSettingsVersion = 0;
		lastReceivedSettingsVersion = 0;
		lastAcknowledgedVersion = 0;
		controllerLockProposal = null;
		clearPendingControllerUnlockKey();
		participantLockProposal = null;
		participantArmedLockId = null;
		participantDeclinedLockId = null;
		lastLockProposalNanos = 0;
		lockSnapshot = RemoteLockSnapshot.inactive();
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
			listener.onRemoteLockChanged(lockSnapshot);
			listener.onRemotePermissionsChanged(localPermissions);
		}
	}

	private void clearPendingControllerUnlockKey()
	{
		if (pendingControllerUnlockKey != null)
		{
			Arrays.fill(pendingControllerUnlockKey, '\0');
			pendingControllerUnlockKey = null;
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
