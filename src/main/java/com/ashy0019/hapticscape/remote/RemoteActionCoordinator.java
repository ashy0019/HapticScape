package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns outbound actions, acknowledgements, and the Live Forge protocol stream. */
final class RemoteActionCoordinator
{
	private static final Logger LOG = Logger.getLogger(RemoteActionCoordinator.class.getName());
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final Gson gson;
	private final Clock clock;
	private final RemoteMessageSender sender;
	private final Consumer<RemoteActionAcknowledgement> acknowledgementPublisher;
	private final RemoteActionService actionService;
	private final RemoteLiveHapticService liveHapticService;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, Long> pendingActions = new LinkedHashMap<>();

	private String controllerLiveStreamId;
	private long controllerLiveSequence;

	RemoteActionCoordinator(
		Gson gson,
		RemoteActionExecutor executor,
		Clock clock,
		RemoteMessageSender sender,
		Consumer<RemoteActionAcknowledgement> acknowledgementPublisher)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.sender = Objects.requireNonNull(sender, "sender");
		this.acknowledgementPublisher = Objects.requireNonNull(
			acknowledgementPublisher,
			"acknowledgementPublisher"
		);
		RemoteActionExecutor requiredExecutor = Objects.requireNonNull(executor, "executor");
		this.actionService = new RemoteActionService(requiredExecutor, clock);
		this.liveHapticService = new RemoteLiveHapticService(requiredExecutor, clock);
	}

	String sendHaptic(
		RemoteRole role,
		RemoteSessionState state,
		String patternSelection,
		int intensityPercent,
		int durationMillis)
	{
		return sendAction(
			role,
			state,
			RemoteAction.haptic(patternSelection, intensityPercent, durationMillis, clock)
		);
	}

	String sendClick(RemoteRole role, RemoteSessionState state)
	{
		return sendAction(role, state, RemoteAction.click(clock));
	}

	String sendMessage(
		RemoteRole role,
		RemoteSessionState state,
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		return sendAction(
			role,
			state,
			RemoteAction.message(
				message,
				desktopNotification,
				localChatboxMessage,
				clock
			)
		);
	}

	String stop(RemoteRole role, RemoteSessionState state)
	{
		clearControllerLiveStream();
		return sendAction(role, state, RemoteAction.stop(clock));
	}

	void beginLive(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions,
		int intensityPercent)
	{
		requireLiveController(role, state, peerPermissions);
		if (controllerLiveStreamId != null)
		{
			endLive(role, state);
		}
		byte[] streamBytes = new byte[12];
		random.nextBytes(streamBytes);
		String streamId = ENCODER.encodeToString(streamBytes);
		RemoteLiveHapticFrame frame = RemoteLiveHapticFrame.start(
			streamId,
			clampLiveIntensity(intensityPercent, peerPermissions),
			clock
		);
		controllerLiveStreamId = streamId;
		controllerLiveSequence = 0;
		if (!sender.send(RemoteMessageType.REMOTE_LIVE_HAPTIC, 0, gson.toJson(frame)))
		{
			clearControllerLiveStream();
			throw new IllegalStateException("Live haptic stream could not be started");
		}
	}

	void updateLive(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions,
		int intensityPercent)
	{
		requireLiveController(role, state, peerPermissions);
		if (controllerLiveStreamId == null)
		{
			throw new IllegalStateException("Live haptic stream is not active");
		}
		long sequence = controllerLiveSequence + 1;
		RemoteLiveHapticFrame frame = RemoteLiveHapticFrame.update(
			controllerLiveStreamId,
			sequence,
			clampLiveIntensity(intensityPercent, peerPermissions),
			clock
		);
		controllerLiveSequence = sequence;
		if (!sender.send(RemoteMessageType.REMOTE_LIVE_HAPTIC, 0, gson.toJson(frame)))
		{
			clearControllerLiveStream();
			throw new IllegalStateException("Live haptic update could not be sent");
		}
	}

	void endLive(RemoteRole role, RemoteSessionState state)
	{
		String streamId = controllerLiveStreamId;
		if (streamId == null)
		{
			return;
		}
		long sequence = controllerLiveSequence + 1;
		clearControllerLiveStream();
		if (role == RemoteRole.CONTROLLER && state == RemoteSessionState.ACTIVE)
		{
			RemoteLiveHapticFrame frame = RemoteLiveHapticFrame.end(streamId, sequence, clock);
			sender.send(RemoteMessageType.REMOTE_LIVE_HAPTIC, 0, gson.toJson(frame));
		}
	}

	void handleAction(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions localPermissions,
		RemoteProtocolMessage message)
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
		if (action != null && action.getType() == RemoteActionType.STOP)
		{
			liveHapticService.stopImmediately();
		}
		RemoteActionAcknowledgement acknowledgement = actionService.process(
			action,
			localPermissions,
			state != RemoteSessionState.ACTIVE
		);
		if (acknowledgement != null)
		{
			sender.send(
				RemoteMessageType.REMOTE_ACTION_ACK,
				0,
				gson.toJson(acknowledgement)
			);
		}
	}

	void handleLive(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions localPermissions,
		RemoteProtocolMessage message)
	{
		if (role != RemoteRole.PARTICIPANT)
		{
			return;
		}
		try
		{
			RemoteLiveHapticFrame frame = gson.fromJson(
				message.getPayload(),
				RemoteLiveHapticFrame.class
			);
			liveHapticService.process(
				frame,
				localPermissions,
				state != RemoteSessionState.ACTIVE
			);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored malformed remote live-haptic frame", e);
		}
	}

	void handleAcknowledgement(RemoteRole role, RemoteProtocolMessage message)
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
			if (pendingActions.remove(acknowledgement.getActionId()) == null)
			{
				return;
			}
			acknowledgementPublisher.accept(acknowledgement);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored invalid remote action acknowledgement", e);
		}
	}

	void tick(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions localPermissions,
		boolean activeTransport)
	{
		if (role == RemoteRole.PARTICIPANT)
		{
			liveHapticService.tick(
				localPermissions,
				state != RemoteSessionState.ACTIVE
			);
		}
		if (activeTransport)
		{
			pendingActions.entrySet().removeIf(
				entry -> clock.millis() > entry.getValue() + 5_000
			);
		}
	}

	void permissionsChanged(RemotePermissions permissions, boolean paused)
	{
		liveHapticService.permissionsChanged(permissions, paused);
	}

	void stopParticipantOutput()
	{
		liveHapticService.stopImmediately();
		actionService.stopOutputSafely();
	}

	void clearControllerLiveStream()
	{
		controllerLiveStreamId = null;
		controllerLiveSequence = 0;
	}

	void reset()
	{
		actionService.reset();
		liveHapticService.reset();
		clearControllerLiveStream();
		pendingActions.clear();
	}

	private String sendAction(
		RemoteRole role,
		RemoteSessionState state,
		RemoteAction action)
	{
		if (role != RemoteRole.CONTROLLER
			|| (state != RemoteSessionState.ACTIVE
				&& !(action.getType() == RemoteActionType.STOP
					&& state == RemoteSessionState.PEER_EMERGENCY_PAUSED)))
		{
			throw new IllegalStateException("A participant must be connected and active");
		}
		action.validate();
		pendingActions.put(action.getActionId(), action.getExpiresAtEpochMillis());
		if (!sender.send(RemoteMessageType.REMOTE_ACTION, 0, gson.toJson(action)))
		{
			pendingActions.remove(action.getActionId());
			throw new IllegalStateException("Remote action could not be sent");
		}
		return action.getActionId();
	}

	private static void requireLiveController(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions peerPermissions)
	{
		if (role != RemoteRole.CONTROLLER || state != RemoteSessionState.ACTIVE)
		{
			throw new IllegalStateException("A participant must be connected and active");
		}
		if (!peerPermissions.isLiveHapticsAllowed())
		{
			throw new IllegalStateException("The participant has not allowed live haptics");
		}
		if (peerPermissions.getMaximumIntensityPercent() <= 0)
		{
			throw new IllegalStateException("The participant's maximum remote intensity is 0%");
		}
	}

	private static int clampLiveIntensity(
		int intensityPercent,
		RemotePermissions peerPermissions)
	{
		return Math.max(
			0,
			Math.min(intensityPercent, peerPermissions.getMaximumIntensityPercent())
		);
	}
}
