package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the initial settings handshake, versioning, and reconciliation protocol. */
final class RemoteSettingsCoordinator
{
	private static final Logger LOG = Logger.getLogger(
		RemoteSettingsCoordinator.class.getName()
	);

	private final Gson gson;
	private final RemoteSettingsStore settingsStore;
	private final EffectiveSettingsService effectiveSettings;
	private final RemoteMessageSender sender;
	private final BiConsumer<RemoteSessionState, String> sessionPublisher;
	private final Consumer<RemoteSettingsSnapshot> settingsPublisher;
	private final Runnable emergencyPause;

	private volatile RemoteSettingsSnapshot lastSentSettings;
	private volatile RemoteSettingsSnapshot controllerSettings;
	private volatile long nextSettingsVersion;
	private volatile long lastReceivedSettingsVersion;
	private volatile long lastAcknowledgedVersion;

	RemoteSettingsCoordinator(
		Gson gson,
		RemoteSettingsStore settingsStore,
		EffectiveSettingsService effectiveSettings,
		RemoteMessageSender sender,
		BiConsumer<RemoteSessionState, String> sessionPublisher,
		Consumer<RemoteSettingsSnapshot> settingsPublisher,
		Runnable emergencyPause)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
		this.effectiveSettings = Objects.requireNonNull(effectiveSettings, "effectiveSettings");
		this.sender = Objects.requireNonNull(sender, "sender");
		this.sessionPublisher = Objects.requireNonNull(sessionPublisher, "sessionPublisher");
		this.settingsPublisher = Objects.requireNonNull(settingsPublisher, "settingsPublisher");
		this.emergencyPause = Objects.requireNonNull(emergencyPause, "emergencyPause");
	}

	RemoteSettingsSnapshot getControllerSettings()
	{
		return controllerSettings;
	}

	boolean hasControllerSettings()
	{
		return controllerSettings != null;
	}

	long sessionVersion(RemoteRole role)
	{
		return role == RemoteRole.CONTROLLER
			? nextSettingsVersion
			: lastReceivedSettingsVersion;
	}

	long getLastReceivedVersion()
	{
		return lastReceivedSettingsVersion;
	}

	boolean updateControllerSetting(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions,
		String key,
		Object value)
	{
		if (role != RemoteRole.CONTROLLER
			|| controllerSettings == null
			|| !peerPermissions.isSettingsAllowed()
			|| (state != RemoteSessionState.ACTIVE
				&& state != RemoteSessionState.PEER_EMERGENCY_PAUSED))
		{
			return false;
		}
		controllerSettings = controllerSettings.withConfigurationValue(gson, key, value);
		return true;
	}

	boolean canSendControllerSettings(
		boolean closed,
		boolean relayOpen,
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions)
	{
		return !closed
			&& role == RemoteRole.CONTROLLER
			&& peerPermissions.isSettingsAllowed()
			&& relayOpen
			&& (state == RemoteSessionState.ACTIVE
				|| state == RemoteSessionState.PEER_EMERGENCY_PAUSED);
	}

	void handleSettings(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions localPermissions,
		RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT
			|| message.getVersion() < lastReceivedSettingsVersion)
		{
			return;
		}
		if (!localPermissions.isSettingsAllowed())
		{
			sender.send(
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
				sender.send(
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
			settingsPublisher.accept(canonical);
			if (state != RemoteSessionState.EMERGENCY_PAUSED)
			{
				sessionPublisher.accept(RemoteSessionState.ACTIVE, "Remote control active");
			}
			sender.send(
				RemoteMessageType.SETTINGS_ACK,
				lastReceivedSettingsVersion,
				gson.toJson(canonical)
			);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid remote settings snapshot", e);
			emergencyPause.run();
		}
	}

	void handleSeed(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions,
		RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER)
		{
			return;
		}
		if (controllerSettings != null)
		{
			if (state == RemoteSessionState.WAITING_FOR_SETTINGS)
			{
				sessionPublisher.accept(RemoteSessionState.ACTIVE, "Participant settings loaded");
			}
			sender.send(RemoteMessageType.SETTINGS_SEED_ACK, 0, "");
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
			settingsPublisher.accept(settings);
			sessionPublisher.accept(RemoteSessionState.ACTIVE, "Participant settings loaded");
			sender.send(RemoteMessageType.SETTINGS_SEED_ACK, 0, "");
			if (peerPermissions.isSettingsAllowed())
			{
				sendSettings(true, role, RemoteSessionState.ACTIVE, peerPermissions);
			}
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Rejected invalid participant settings seed", e);
			sessionPublisher.accept(
				RemoteSessionState.DISCONNECTED,
				"Participant settings could not be loaded"
			);
		}
	}

	void handleSeedAcknowledgement(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions localPermissions)
	{
		if (role == RemoteRole.PARTICIPANT
			&& state == RemoteSessionState.WAITING_FOR_SETTINGS)
		{
			sessionPublisher.accept(
				RemoteSessionState.ACTIVE,
				localPermissions.isSettingsAllowed()
					? "Remote control active"
					: "Remote actions active; settings changes disabled"
			);
		}
	}

	void handleAcknowledgement(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions,
		RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER)
		{
			return;
		}
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
					settingsPublisher.accept(canonical);
				}
			}
			sessionPublisher.accept(state, statusForController(state, peerPermissions));
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.WARNING, "Ignored invalid settings acknowledgement", e);
		}
	}

	void handleRejected(RemoteRole role, RemoteSessionState state)
	{
		if (role == RemoteRole.CONTROLLER)
		{
			sessionPublisher.accept(state, "Participant disabled remote settings");
		}
	}

	void sendSeed(RemoteRole role)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		RemoteSettingsSnapshot settings = settingsStore.capture();
		settings.validate();
		sender.send(RemoteMessageType.SETTINGS_SEED, 0, gson.toJson(settings));
	}

	void sendSettings(
		boolean force,
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions)
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
				sender.send(
					RemoteMessageType.SETTINGS,
					nextSettingsVersion,
					gson.toJson(settings)
				);
				sessionPublisher.accept(state, statusForController(state, peerPermissions));
			}
			return;
		}
		long version = nextSettingsVersion + 1;
		RemoteSettingsSnapshot previousSent = lastSentSettings;
		long previousVersion = nextSettingsVersion;
		lastSentSettings = settings;
		nextSettingsVersion = version;
		if (!sender.send(RemoteMessageType.SETTINGS, version, gson.toJson(settings)))
		{
			if (nextSettingsVersion == version && Objects.equals(lastSentSettings, settings))
			{
				lastSentSettings = previousSent;
				nextSettingsVersion = previousVersion;
			}
		}
		sessionPublisher.accept(state, statusForController(state, peerPermissions));
	}

	String statusForController(
		RemoteSessionState state,
		RemotePermissions peerPermissions)
	{
		if (state == RemoteSessionState.PEER_EMERGENCY_PAUSED)
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

	void reset()
	{
		lastSentSettings = null;
		controllerSettings = null;
		nextSettingsVersion = 0;
		lastReceivedSettingsVersion = 0;
		lastAcknowledgedVersion = 0;
	}
}
