package com.ashy0019.hapticscape.remote;

import java.time.Clock;
import java.util.Objects;

/** One short-lived sample in an encrypted remote Live Forge stream. */
final class RemoteLiveHapticFrame
{
	static final int SCHEMA_VERSION = 1;
	static final long FRAME_LIFETIME_MILLIS = 750;
	private static final int MAXIMUM_STREAM_ID_LENGTH = 64;

	private final int schemaVersion;
	private final RemoteLiveHapticPhase phase;
	private final String streamId;
	private final long sequence;
	private final long createdAtEpochMillis;
	private final long expiresAtEpochMillis;
	private final int intensityPercent;

	private RemoteLiveHapticFrame(
		int schemaVersion,
		RemoteLiveHapticPhase phase,
		String streamId,
		long sequence,
		long createdAtEpochMillis,
		long expiresAtEpochMillis,
		int intensityPercent)
	{
		this.schemaVersion = schemaVersion;
		this.phase = phase;
		this.streamId = streamId;
		this.sequence = sequence;
		this.createdAtEpochMillis = createdAtEpochMillis;
		this.expiresAtEpochMillis = expiresAtEpochMillis;
		this.intensityPercent = intensityPercent;
		validate();
	}

	static RemoteLiveHapticFrame start(String streamId, int intensityPercent, Clock clock)
	{
		return create(RemoteLiveHapticPhase.START, streamId, 0, intensityPercent, clock);
	}

	static RemoteLiveHapticFrame update(
		String streamId,
		long sequence,
		int intensityPercent,
		Clock clock)
	{
		return create(
			RemoteLiveHapticPhase.UPDATE,
			streamId,
			sequence,
			intensityPercent,
			clock
		);
	}

	static RemoteLiveHapticFrame end(String streamId, long sequence, Clock clock)
	{
		return create(RemoteLiveHapticPhase.END, streamId, sequence, 0, clock);
	}

	private static RemoteLiveHapticFrame create(
		RemoteLiveHapticPhase phase,
		String streamId,
		long sequence,
		int intensityPercent,
		Clock clock)
	{
		long created = Objects.requireNonNull(clock, "clock").millis();
		return new RemoteLiveHapticFrame(
			SCHEMA_VERSION,
			phase,
			streamId,
			sequence,
			created,
			created + FRAME_LIFETIME_MILLIS,
			intensityPercent
		);
	}

	static RemoteLiveHapticFrame forTest(
		RemoteLiveHapticPhase phase,
		String streamId,
		long sequence,
		long createdAtEpochMillis,
		long expiresAtEpochMillis,
		int intensityPercent)
	{
		return new RemoteLiveHapticFrame(
			SCHEMA_VERSION,
			phase,
			streamId,
			sequence,
			createdAtEpochMillis,
			expiresAtEpochMillis,
			intensityPercent
		);
	}

	void validate()
	{
		if (schemaVersion != SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported live-haptic schema");
		}
		if (phase == null)
		{
			throw new IllegalArgumentException("Live-haptic phase is required");
		}
		if (streamId == null || streamId.isEmpty()
			|| streamId.length() > MAXIMUM_STREAM_ID_LENGTH
			|| !streamId.matches("[A-Za-z0-9_-]+"))
		{
			throw new IllegalArgumentException("Invalid live-haptic stream ID");
		}
		if (sequence < 0
			|| (phase == RemoteLiveHapticPhase.START && sequence != 0)
			|| (phase != RemoteLiveHapticPhase.START && sequence == 0))
		{
			throw new IllegalArgumentException("Invalid live-haptic sequence");
		}
		if (createdAtEpochMillis < 0
			|| expiresAtEpochMillis < createdAtEpochMillis
			|| expiresAtEpochMillis - createdAtEpochMillis > FRAME_LIFETIME_MILLIS)
		{
			throw new IllegalArgumentException("Invalid live-haptic lifetime");
		}
		if (intensityPercent < 0 || intensityPercent > 100
			|| (phase == RemoteLiveHapticPhase.END && intensityPercent != 0))
		{
			throw new IllegalArgumentException("Invalid live-haptic intensity");
		}
	}

	RemoteLiveHapticPhase getPhase()
	{
		return phase;
	}

	String getStreamId()
	{
		return streamId;
	}

	long getSequence()
	{
		return sequence;
	}

	long getCreatedAtEpochMillis()
	{
		return createdAtEpochMillis;
	}

	long getExpiresAtEpochMillis()
	{
		return expiresAtEpochMillis;
	}

	int getIntensityPercent()
	{
		return intensityPercent;
	}
}
