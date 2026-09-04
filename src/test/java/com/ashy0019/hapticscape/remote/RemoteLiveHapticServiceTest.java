package com.ashy0019.hapticscape.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RemoteLiveHapticServiceTest
{
	private static final long NOW = 1_800_000_000_000L;

	@Test
	public void streamIsClampedOrderedAndReleased()
	{
		MutableClock clock = new MutableClock(NOW);
		RecordingExecutor executor = new RecordingExecutor();
		RemoteLiveHapticService service = new RemoteLiveHapticService(executor, clock);
		RemotePermissions permissions = livePermissions(60, 30_000);

		assertTrue(service.process(frame(RemoteLiveHapticPhase.START, "stream-one", 0, 90), permissions, false));
		assertTrue(service.process(frame(RemoteLiveHapticPhase.UPDATE, "stream-one", 1, 40), permissions, false));
		assertFalse(service.process(frame(RemoteLiveHapticPhase.UPDATE, "stream-one", 1, 80), permissions, false));
		assertFalse(service.process(frame(RemoteLiveHapticPhase.UPDATE, "another-stream", 2, 80), permissions, false));
		assertTrue(service.process(frame(RemoteLiveHapticPhase.END, "stream-one", 2, 0), permissions, false));

		assertEquals(java.util.Arrays.asList(60, 40), executor.intensities);
		assertEquals(1, executor.releaseCount);
		assertEquals(0, executor.stopLiveCount);
	}

	@Test
	public void watchdogAndMaximumHoldReleaseLocally()
	{
		MutableClock clock = new MutableClock(NOW);
		RecordingExecutor executor = new RecordingExecutor();
		RemoteLiveHapticService service = new RemoteLiveHapticService(executor, clock);
		RemotePermissions permissions = livePermissions(75, 1_000);

		service.process(frame(RemoteLiveHapticPhase.START, "watchdog", 0, 50), permissions, false);
		clock.advance(RemoteLiveHapticService.WATCHDOG_MILLIS);
		service.tick(permissions, false);
		assertEquals(1, executor.releaseCount);

		service.process(frame(RemoteLiveHapticPhase.START, "maximum-hold", 0, 50), permissions, false);
		clock.advance(900);
		service.process(frame(RemoteLiveHapticPhase.UPDATE, "maximum-hold", 1, 50), permissions, false);
		clock.advance(100);
		service.tick(permissions, false);
		assertEquals(2, executor.releaseCount);
	}

	@Test
	public void permissionAndEmergencyChangesStopImmediately()
	{
		MutableClock clock = new MutableClock(NOW);
		RecordingExecutor executor = new RecordingExecutor();
		RemoteLiveHapticService service = new RemoteLiveHapticService(executor, clock);
		RemotePermissions allowed = livePermissions(80, 30_000);

		service.process(frame(RemoteLiveHapticPhase.START, "permission", 0, 70), allowed, false);
		service.permissionsChanged(RemotePermissions.defaults(), false);
		assertEquals(1, executor.stopLiveCount);

		service.process(frame(RemoteLiveHapticPhase.START, "paused", 0, 70), allowed, false);
		service.tick(allowed, true);
		assertEquals(2, executor.stopLiveCount);
	}

	@Test
	public void reducedIntensityCapIsAppliedDuringStream()
	{
		MutableClock clock = new MutableClock(NOW);
		RecordingExecutor executor = new RecordingExecutor();
		RemoteLiveHapticService service = new RemoteLiveHapticService(executor, clock);
		service.process(
			frame(RemoteLiveHapticPhase.START, "cap-change", 0, 70),
			livePermissions(80, 30_000),
			false
		);

		service.permissionsChanged(livePermissions(30, 30_000), false);

		assertEquals(java.util.Arrays.asList(70, 30), executor.intensities);
	}

	@Test
	public void expiredFutureAndPreviouslySeenStartsAreIgnored()
	{
		MutableClock clock = new MutableClock(NOW);
		RecordingExecutor executor = new RecordingExecutor();
		RemoteLiveHapticService service = new RemoteLiveHapticService(executor, clock);
		RemotePermissions permissions = livePermissions(80, 30_000);
		RemoteLiveHapticFrame expired = RemoteLiveHapticFrame.forTest(
			RemoteLiveHapticPhase.START,
			"expired-stream",
			0,
			NOW - 3_000,
			NOW - 2_250,
			50
		);
		RemoteLiveHapticFrame future = RemoteLiveHapticFrame.forTest(
			RemoteLiveHapticPhase.START,
			"future-stream",
			0,
			NOW + 3_000,
			NOW + 3_750,
			50
		);

		assertFalse(service.process(expired, permissions, false));
		assertFalse(service.process(future, permissions, false));
		assertTrue(service.process(frame(RemoteLiveHapticPhase.START, "seen-stream", 0, 50), permissions, false));
		assertTrue(service.process(frame(RemoteLiveHapticPhase.END, "seen-stream", 1, 0), permissions, false));
		assertFalse(service.process(frame(RemoteLiveHapticPhase.START, "seen-stream", 0, 50), permissions, false));
		assertEquals(1, executor.intensities.size());
	}

	private static RemotePermissions livePermissions(int maximumIntensity, int maximumLiveMillis)
	{
		return new RemotePermissions(
			true, true, true, true, true, false,
			maximumIntensity, 3_000, maximumLiveMillis
		);
	}

	private static RemoteLiveHapticFrame frame(
		RemoteLiveHapticPhase phase,
		String streamId,
		long sequence,
		int intensity)
	{
		return RemoteLiveHapticFrame.forTest(
			phase,
			streamId,
			sequence,
			NOW,
			NOW + RemoteLiveHapticFrame.FRAME_LIFETIME_MILLIS,
			intensity
		);
	}

	private static final class RecordingExecutor implements RemoteActionExecutor
	{
		private final List<Integer> intensities = new ArrayList<>();
		private int releaseCount;
		private int stopLiveCount;

		@Override public void playHaptic(String pattern, int intensity, int duration) { }
		@Override public void playClick() { }
		@Override public void showMessage(String message, boolean desktop, boolean chatbox) { }
		@Override public void stopRemoteOutput() { }

		@Override
		public void setRemoteLiveIntensity(int intensityPercent)
		{
			intensities.add(intensityPercent);
		}

		@Override
		public void releaseRemoteLiveHaptic()
		{
			releaseCount++;
		}

		@Override
		public void stopRemoteLiveHaptic()
		{
			stopLiveCount++;
		}
	}

	private static final class MutableClock extends Clock
	{
		private long millis;

		private MutableClock(long millis)
		{
			this.millis = millis;
		}

		private void advance(long amount)
		{
			millis += amount;
		}

		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return Instant.ofEpochMilli(millis); }
		@Override public long millis() { return millis; }
	}
}
