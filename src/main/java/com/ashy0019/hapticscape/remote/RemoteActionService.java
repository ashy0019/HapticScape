package com.ashy0019.hapticscape.remote;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Receiver-side validation and safety policy for immediate remote actions.
 */
final class RemoteActionService
{
	private static final long MAXIMUM_CLOCK_SKEW_MILLIS = 2_000;
	private static final int MAXIMUM_REPLAY_ENTRIES = 256;
	private static final long RATE_WINDOW_MILLIS = 10_000;

	private final RemoteActionExecutor executor;
	private final Clock clock;
	private final Map<String, RemoteActionAcknowledgement> processed =
		new LinkedHashMap<>();
	private final Map<RemoteActionType, Deque<Long>> recentActions =
		new EnumMap<>(RemoteActionType.class);

	RemoteActionService(RemoteActionExecutor executor, Clock clock)
	{
		this.executor = Objects.requireNonNull(executor, "executor");
		this.clock = Objects.requireNonNull(clock, "clock");
		for (RemoteActionType type : RemoteActionType.values())
		{
			recentActions.put(type, new ArrayDeque<>());
		}
	}

	synchronized RemoteActionAcknowledgement process(
		RemoteAction action,
		RemotePermissions permissions,
		boolean emergencyPaused)
	{
		if (action == null || action.getActionId() == null
			|| action.getActionId().isEmpty() || action.getActionId().length() > 64)
		{
			return null;
		}

		RemoteActionAcknowledgement previous = processed.get(action.getActionId());
		if (previous != null)
		{
			return previous;
		}

		try
		{
			action.validate();
			permissions.validate();
		}
		catch (RuntimeException invalid)
		{
			return record(ack(action, RemoteActionResult.INVALID, "Invalid action", 0, 0));
		}

		long now = clock.millis();
		if (action.getCreatedAtEpochMillis() > now + MAXIMUM_CLOCK_SKEW_MILLIS)
		{
			return record(ack(action, RemoteActionResult.INVALID, "Action clock is invalid", 0, 0));
		}
		if (now > action.getExpiresAtEpochMillis() + MAXIMUM_CLOCK_SKEW_MILLIS)
		{
			return record(ack(action, RemoteActionResult.EXPIRED, "Action expired", 0, 0));
		}

		if (action.getType() == RemoteActionType.STOP)
		{
			return executeStop(action);
		}
		if (emergencyPaused)
		{
			return record(ack(action, RemoteActionResult.PAUSED, "Emergency Off is active", 0, 0));
		}
		if (!allowByRate(action.getType(), now))
		{
			return record(ack(action, RemoteActionResult.RATE_LIMITED, "Action rate limited", 0, 0));
		}

		try
		{
			switch (action.getType())
			{
				case HAPTIC:
					return executeHaptic(action, permissions);
				case CLICK:
					return executeClick(action, permissions);
				case MESSAGE:
					return executeMessage(action, permissions);
				default:
					return record(ack(action, RemoteActionResult.INVALID, "Unsupported action", 0, 0));
			}
		}
		catch (RuntimeException failure)
		{
			return record(ack(action, RemoteActionResult.FAILED, "Local action failed", 0, 0));
		}
	}

	synchronized void reset()
	{
		processed.clear();
		for (Deque<Long> timestamps : recentActions.values())
		{
			timestamps.clear();
		}
	}

	void stopOutputSafely()
	{
		try
		{
			executor.stopRemoteOutput();
		}
		catch (RuntimeException ignored)
		{
			// Session shutdown and Emergency Off must continue even if local cleanup fails.
		}
	}

	private RemoteActionAcknowledgement executeHaptic(
		RemoteAction action,
		RemotePermissions permissions)
	{
		if (!permissions.isHapticsAllowed())
		{
			return record(ack(action, RemoteActionResult.DENIED, "Remote haptics not allowed", 0, 0));
		}
		int intensity = Math.min(
			action.getIntensityPercent(),
			permissions.getMaximumIntensityPercent()
		);
		int duration = Math.min(
			action.getDurationMillis(),
			permissions.getMaximumDurationMillis()
		);
		executor.playHaptic(action.getPatternSelection(), intensity, duration);
		boolean limited = intensity != action.getIntensityPercent()
			|| duration != action.getDurationMillis();
		return record(ack(
			action,
			limited ? RemoteActionResult.LIMITED : RemoteActionResult.EXECUTED,
			limited ? "Applied participant safety limits" : "Haptic action executed",
			intensity,
			duration
		));
	}

	private RemoteActionAcknowledgement executeClick(
		RemoteAction action,
		RemotePermissions permissions)
	{
		if (!permissions.isClicksAllowed())
		{
			return record(ack(action, RemoteActionResult.DENIED, "Remote clicks not allowed", 0, 0));
		}
		executor.playClick();
		return record(ack(action, RemoteActionResult.EXECUTED, "Click executed", 0, 0));
	}

	private RemoteActionAcknowledgement executeMessage(
		RemoteAction action,
		RemotePermissions permissions)
	{
		boolean desktop = action.isDesktopNotification()
			&& permissions.isDesktopNotificationsAllowed();
		boolean chatbox = action.isLocalChatboxMessage()
			&& permissions.isLocalChatboxMessagesAllowed();
		if (!desktop && !chatbox)
		{
			return record(ack(action, RemoteActionResult.DENIED, "Remote messages not allowed", 0, 0));
		}
		String message = RemoteTextSanitizer.sanitize(action.getMessage());
		if (message.isEmpty())
		{
			return record(ack(action, RemoteActionResult.INVALID, "Message was empty after sanitizing", 0, 0));
		}
		executor.showMessage(message, desktop, chatbox);
		boolean limited = desktop != action.isDesktopNotification()
			|| chatbox != action.isLocalChatboxMessage();
		return record(ack(
			action,
			limited ? RemoteActionResult.LIMITED : RemoteActionResult.EXECUTED,
			limited ? "Shown only in allowed destinations" : "Message shown locally",
			0,
			0
		));
	}

	private RemoteActionAcknowledgement executeStop(RemoteAction action)
	{
		try
		{
			executor.stopRemoteOutput();
			return record(ack(action, RemoteActionResult.EXECUTED, "Remote output stopped", 0, 0));
		}
		catch (RuntimeException failure)
		{
			return record(ack(action, RemoteActionResult.FAILED, "Unable to stop remote output", 0, 0));
		}
	}

	private boolean allowByRate(RemoteActionType type, long now)
	{
		Deque<Long> timestamps = recentActions.get(type);
		while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= RATE_WINDOW_MILLIS)
		{
			timestamps.removeFirst();
		}
		int maximum;
		switch (type)
		{
			case HAPTIC:
				maximum = 8;
				break;
			case CLICK:
				maximum = 12;
				break;
			case MESSAGE:
				maximum = 4;
				break;
			default:
				return true;
		}
		if (timestamps.size() >= maximum)
		{
			return false;
		}
		timestamps.addLast(now);
		return true;
	}

	private RemoteActionAcknowledgement record(RemoteActionAcknowledgement acknowledgement)
	{
		processed.put(acknowledgement.getActionId(), acknowledgement);
		while (processed.size() > MAXIMUM_REPLAY_ENTRIES)
		{
			String oldest = processed.keySet().iterator().next();
			processed.remove(oldest);
		}
		return acknowledgement;
	}

	private static RemoteActionAcknowledgement ack(
		RemoteAction action,
		RemoteActionResult result,
		String message,
		int intensity,
		int duration)
	{
		return new RemoteActionAcknowledgement(
			action.getActionId(),
			result,
			message,
			intensity,
			duration
		);
	}
}
