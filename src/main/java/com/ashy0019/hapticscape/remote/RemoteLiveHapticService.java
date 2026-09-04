package com.ashy0019.hapticscape.remote;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Receiver-owned validation and dead-man behavior for one live haptic stream. */
final class RemoteLiveHapticService
{
	static final long WATCHDOG_MILLIS = 350;
	private static final long MAXIMUM_CLOCK_SKEW_MILLIS = 2_000;
	private static final int MAXIMUM_SEEN_STREAMS = 64;

	private final RemoteActionExecutor executor;
	private final Clock clock;
	private final Set<String> seenStreamIds = new LinkedHashSet<>();

	private String activeStreamId;
	private long activeStartedAtMillis;
	private long lastFrameAtMillis;
	private long lastSequence;
	private long latestStartCreatedAtMillis = -1;
	private int appliedIntensityPercent;

	RemoteLiveHapticService(RemoteActionExecutor executor, Clock clock)
	{
		this.executor = Objects.requireNonNull(executor, "executor");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	synchronized boolean process(
		RemoteLiveHapticFrame frame,
		RemotePermissions permissions,
		boolean emergencyPaused)
	{
		Objects.requireNonNull(permissions, "permissions").validate();
		if (frame == null)
		{
			return false;
		}
		try
		{
			frame.validate();
		}
		catch (RuntimeException invalid)
		{
			return false;
		}

		long now = clock.millis();
		if (frame.getCreatedAtEpochMillis() > now + MAXIMUM_CLOCK_SKEW_MILLIS
			|| now > frame.getExpiresAtEpochMillis() + MAXIMUM_CLOCK_SKEW_MILLIS)
		{
			return false;
		}

		if (frame.getPhase() == RemoteLiveHapticPhase.END)
		{
			if (!matchesActive(frame) || frame.getSequence() <= lastSequence)
			{
				return false;
			}
			releaseAndClear();
			return true;
		}

		if (emergencyPaused || !permissions.isLiveHapticsAllowed())
		{
			stopImmediately();
			return false;
		}

		if (frame.getPhase() == RemoteLiveHapticPhase.START)
		{
			return start(frame, permissions, now);
		}
		if (!matchesActive(frame) || frame.getSequence() <= lastSequence)
		{
			return false;
		}
		if (now - activeStartedAtMillis >= permissions.getMaximumLiveDurationMillis())
		{
			releaseAndClear();
			return false;
		}

		lastSequence = frame.getSequence();
		lastFrameAtMillis = now;
		applyIntensity(frame.getIntensityPercent(), permissions);
		return true;
	}

	synchronized void tick(RemotePermissions permissions, boolean emergencyPaused)
	{
		if (activeStreamId == null)
		{
			return;
		}
		long now = clock.millis();
		if (emergencyPaused || !permissions.isLiveHapticsAllowed())
		{
			stopImmediately();
		}
		else if (now - lastFrameAtMillis >= WATCHDOG_MILLIS
			|| now - activeStartedAtMillis >= permissions.getMaximumLiveDurationMillis())
		{
			releaseAndClear();
		}
		else if (appliedIntensityPercent > permissions.getMaximumIntensityPercent())
		{
			applyIntensity(appliedIntensityPercent, permissions);
		}
	}

	synchronized void permissionsChanged(
		RemotePermissions permissions,
		boolean emergencyPaused)
	{
		tick(permissions, emergencyPaused);
	}

	synchronized void stopImmediately()
	{
		if (activeStreamId != null)
		{
			try
			{
				executor.stopRemoteLiveHaptic();
			}
			catch (RuntimeException ignored)
			{
				// Safety cleanup must still discard the active stream state.
			}
		}
		clearActive();
	}

	synchronized void reset()
	{
		stopImmediately();
		seenStreamIds.clear();
		latestStartCreatedAtMillis = -1;
	}

	private boolean start(
		RemoteLiveHapticFrame frame,
		RemotePermissions permissions,
		long now)
	{
		if (seenStreamIds.contains(frame.getStreamId())
			|| frame.getCreatedAtEpochMillis() < latestStartCreatedAtMillis)
		{
			return false;
		}
		if (activeStreamId != null)
		{
			stopImmediately();
		}
		rememberStream(frame.getStreamId());
		latestStartCreatedAtMillis = frame.getCreatedAtEpochMillis();
		activeStreamId = frame.getStreamId();
		activeStartedAtMillis = now;
		lastFrameAtMillis = now;
		lastSequence = frame.getSequence();
		applyIntensity(frame.getIntensityPercent(), permissions);
		return true;
	}

	private boolean matchesActive(RemoteLiveHapticFrame frame)
	{
		return activeStreamId != null && activeStreamId.equals(frame.getStreamId());
	}

	private void applyIntensity(int requestedPercent, RemotePermissions permissions)
	{
		appliedIntensityPercent = Math.min(
			requestedPercent,
			permissions.getMaximumIntensityPercent()
		);
		executor.setRemoteLiveIntensity(appliedIntensityPercent);
	}

	private void releaseAndClear()
	{
		try
		{
			executor.releaseRemoteLiveHaptic();
		}
		catch (RuntimeException ignored)
		{
			try
			{
				executor.stopRemoteLiveHaptic();
			}
			catch (RuntimeException alsoIgnored)
			{
				// Receiver state is cleared even if the local device call fails.
			}
		}
		clearActive();
	}

	private void clearActive()
	{
		activeStreamId = null;
		activeStartedAtMillis = 0;
		lastFrameAtMillis = 0;
		lastSequence = 0;
		appliedIntensityPercent = 0;
	}

	private void rememberStream(String streamId)
	{
		seenStreamIds.add(streamId);
		while (seenStreamIds.size() > MAXIMUM_SEEN_STREAMS)
		{
			String oldest = seenStreamIds.iterator().next();
			seenStreamIds.remove(oldest);
		}
	}
}
