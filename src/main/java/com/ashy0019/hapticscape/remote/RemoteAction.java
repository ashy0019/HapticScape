package com.ashy0019.hapticscape.remote;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** One immediate, encrypted and short-lived Remote Control command. */
public final class RemoteAction
{
	public static final int SCHEMA_VERSION = 1;
	public static final long LIFETIME_MILLIS = 3_000;
	public static final int MAXIMUM_MESSAGE_LENGTH = 200;
	private static final int MAXIMUM_PATTERN_VALUE_LENGTH = 32;

	private final int schemaVersion;
	private final String actionId;
	private final RemoteActionType type;
	private final long createdAtEpochMillis;
	private final long expiresAtEpochMillis;
	private final String patternSelection;
	private final int intensityPercent;
	private final int durationMillis;
	private final String message;
	private final boolean desktopNotification;
	private final boolean localChatboxMessage;

	private RemoteAction(
		int schemaVersion,
		String actionId,
		RemoteActionType type,
		long createdAtEpochMillis,
		long expiresAtEpochMillis,
		String patternSelection,
		int intensityPercent,
		int durationMillis,
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		this.schemaVersion = schemaVersion;
		this.actionId = actionId;
		this.type = type;
		this.createdAtEpochMillis = createdAtEpochMillis;
		this.expiresAtEpochMillis = expiresAtEpochMillis;
		this.patternSelection = patternSelection;
		this.intensityPercent = intensityPercent;
		this.durationMillis = durationMillis;
		this.message = message;
		this.desktopNotification = desktopNotification;
		this.localChatboxMessage = localChatboxMessage;
	}

	public static RemoteAction haptic(
		String patternSelection,
		int intensityPercent,
		int durationMillis)
	{
		return haptic(patternSelection, intensityPercent, durationMillis, Clock.systemUTC());
	}

	static RemoteAction haptic(
		String patternSelection,
		int intensityPercent,
		int durationMillis,
		Clock clock)
	{
		return create(
			RemoteActionType.HAPTIC,
			Objects.requireNonNull(clock, "clock"),
			patternSelection,
			intensityPercent,
			durationMillis,
			"",
			false,
			false
		);
	}

	public static RemoteAction click()
	{
		return click(Clock.systemUTC());
	}

	static RemoteAction click(Clock clock)
	{
		return create(RemoteActionType.CLICK, clock, "", 0, 0, "", false, false);
	}

	public static RemoteAction message(
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		return message(message, desktopNotification, localChatboxMessage, Clock.systemUTC());
	}

	static RemoteAction message(
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage,
		Clock clock)
	{
		return create(
			RemoteActionType.MESSAGE,
			clock,
			"",
			0,
			0,
			message,
			desktopNotification,
			localChatboxMessage
		);
	}

	public static RemoteAction stop()
	{
		return stop(Clock.systemUTC());
	}

	static RemoteAction stop(Clock clock)
	{
		return create(RemoteActionType.STOP, clock, "", 0, 0, "", false, false);
	}

	static RemoteAction forTest(
		String actionId,
		RemoteActionType type,
		long createdAtEpochMillis,
		long expiresAtEpochMillis,
		String patternSelection,
		int intensityPercent,
		int durationMillis,
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		return new RemoteAction(
			SCHEMA_VERSION,
			actionId,
			type,
			createdAtEpochMillis,
			expiresAtEpochMillis,
			patternSelection,
			intensityPercent,
			durationMillis,
			message,
			desktopNotification,
			localChatboxMessage
		);
	}

	private static RemoteAction create(
		RemoteActionType type,
		Clock clock,
		String patternSelection,
		int intensityPercent,
		int durationMillis,
		String message,
		boolean desktopNotification,
		boolean localChatboxMessage)
	{
		long now = Objects.requireNonNull(clock, "clock").millis();
		RemoteAction action = new RemoteAction(
			SCHEMA_VERSION,
			UUID.randomUUID().toString(),
			Objects.requireNonNull(type, "type"),
			now,
			now + LIFETIME_MILLIS,
			patternSelection == null ? "" : patternSelection.trim().toUpperCase(Locale.ROOT),
			intensityPercent,
			durationMillis,
			message == null ? "" : message,
			desktopNotification,
			localChatboxMessage
		);
		action.validate();
		return action;
	}

	public void validate()
	{
		if (schemaVersion != SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported remote-action schema");
		}
		if (actionId == null || actionId.isEmpty() || actionId.length() > 64)
		{
			throw new IllegalArgumentException("Remote action id is invalid");
		}
		if (type == null || createdAtEpochMillis <= 0
			|| expiresAtEpochMillis <= createdAtEpochMillis
			|| expiresAtEpochMillis - createdAtEpochMillis > LIFETIME_MILLIS)
		{
			throw new IllegalArgumentException("Remote action lifetime is invalid");
		}
		switch (type)
		{
			case HAPTIC:
				validateHaptic();
				break;
			case MESSAGE:
				if (message == null || message.trim().isEmpty()
					|| message.length() > MAXIMUM_MESSAGE_LENGTH
					|| (!desktopNotification && !localChatboxMessage))
				{
					throw new IllegalArgumentException("Remote message payload is invalid");
				}
				break;
			case CLICK:
			case STOP:
				break;
			default:
				throw new IllegalArgumentException("Remote action type is invalid");
		}
	}

	private void validateHaptic()
	{
		if (patternSelection == null || patternSelection.isEmpty()
			|| patternSelection.length() > MAXIMUM_PATTERN_VALUE_LENGTH
			|| !patternSelection.matches(
				"SINGLE|DOUBLE|TRIPLE|ASCENDING|DESCENDING|CUSTOM:[1-9][0-9]*"
			)
			|| intensityPercent < 0 || intensityPercent > 100
			|| durationMillis < RemotePermissions.MINIMUM_DURATION_MILLIS
			|| durationMillis > RemotePermissions.MAXIMUM_DURATION_MILLIS)
		{
			throw new IllegalArgumentException("Remote haptic payload is invalid");
		}
	}

	public String getActionId() { return actionId; }
	public RemoteActionType getType() { return type; }
	public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
	public long getExpiresAtEpochMillis() { return expiresAtEpochMillis; }
	public String getPatternSelection() { return patternSelection; }
	public int getIntensityPercent() { return intensityPercent; }
	public int getDurationMillis() { return durationMillis; }
	public String getMessage() { return message; }
	public boolean isDesktopNotification() { return desktopNotification; }
	public boolean isLocalChatboxMessage() { return localChatboxMessage; }
}
