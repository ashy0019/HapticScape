package com.ashy0019.hapticscape.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Test;

public class RemoteActionServiceTest
{
	private static final long NOW = 1_800_000_000_000L;

	@Test
	public void hapticIsClampedByParticipantAndDuplicateIsNotReplayed()
	{
		RecordingExecutor executor = new RecordingExecutor();
		RemoteActionService service = service(executor);
		RemoteAction action = action(
			"bounded-haptic",
			RemoteActionType.HAPTIC,
			NOW,
			NOW + 3_000,
			"TRIPLE",
			90,
			8_000,
			"",
			false,
			false
		);
		RemotePermissions permissions = new RemotePermissions(
			true, true, true, true, false, 55, 1_500
		);

		RemoteActionAcknowledgement first = service.process(action, permissions, false);
		RemoteActionAcknowledgement duplicate = service.process(action, permissions, false);

		assertEquals(RemoteActionResult.LIMITED, first.getResult());
		assertEquals(55, first.getAppliedIntensityPercent());
		assertEquals(1_500, first.getAppliedDurationMillis());
		assertEquals(1, executor.hapticCount);
		assertEquals("TRIPLE", executor.pattern);
		assertSame(first, duplicate);
	}

	@Test
	public void expiredAndFutureActionsNeverExecute()
	{
		RecordingExecutor executor = new RecordingExecutor();
		RemoteActionService service = service(executor);
		RemotePermissions permissions = RemotePermissions.defaults();
		RemoteAction expired = action(
			"expired", RemoteActionType.CLICK, NOW - 6_000, NOW - 3_000,
			"", 0, 0, "", false, false
		);
		RemoteAction future = action(
			"future", RemoteActionType.CLICK, NOW + 6_000, NOW + 9_000,
			"", 0, 0, "", false, false
		);

		assertEquals(RemoteActionResult.EXPIRED,
			service.process(expired, permissions, false).getResult());
		assertEquals(RemoteActionResult.INVALID,
			service.process(future, permissions, false).getResult());
		assertEquals(0, executor.clickCount);
	}

	@Test
	public void permissionsAndEmergencyPauseAreReceiverAuthoritative()
	{
		RecordingExecutor executor = new RecordingExecutor();
		RemoteActionService service = service(executor);
		RemotePermissions denied = new RemotePermissions(
			true, false, false, false, false, 100, 10_000
		);

		assertEquals(RemoteActionResult.DENIED,
			service.process(RemoteAction.haptic("SINGLE", 50, 500, fixedClock()), denied, false)
				.getResult());
		assertEquals(RemoteActionResult.DENIED,
			service.process(RemoteAction.click(fixedClock()), denied, false).getResult());
		assertEquals(RemoteActionResult.PAUSED,
			service.process(RemoteAction.click(fixedClock()), RemotePermissions.defaults(), true)
				.getResult());
		assertEquals(0, executor.hapticCount);
		assertEquals(0, executor.clickCount);
	}

	@Test
	public void stopAlwaysWorksIncludingDuringEmergencyPause()
	{
		RecordingExecutor executor = new RecordingExecutor();
		RemoteActionService service = service(executor);
		RemoteActionAcknowledgement acknowledgement = service.process(
			RemoteAction.stop(fixedClock()),
			RemotePermissions.none(),
			true
		);

		assertEquals(RemoteActionResult.EXECUTED, acknowledgement.getResult());
		assertEquals(1, executor.stopCount);
	}

	@Test
	public void messageIsSanitizedAndRestrictedToAllowedDestinations()
	{
		RecordingExecutor executor = new RecordingExecutor();
		RemoteActionService service = service(executor);
		RemotePermissions permissions = new RemotePermissions(
			true, true, true, false, true, 60, 3_000
		);
		RemoteAction action = RemoteAction.message(
			"  <col=ff0000>Hello</col>\n\u202eHTTPS://example.com  ",
			true,
			true,
			fixedClock()
		);

		RemoteActionAcknowledgement acknowledgement = service.process(
			action,
			permissions,
			false
		);

		assertEquals(RemoteActionResult.LIMITED, acknowledgement.getResult());
		assertFalse(executor.desktop);
		assertTrue(executor.chatbox);
		assertEquals("Hello https[:]//example.com", executor.message);
	}

	@Test
	public void receiverRateLimitsBursts()
	{
		RecordingExecutor executor = new RecordingExecutor();
		RemoteActionService service = service(executor);
		RemoteActionAcknowledgement last = null;
		for (int index = 0; index < 13; index++)
		{
			last = service.process(RemoteAction.click(fixedClock()), RemotePermissions.defaults(), false);
		}
		assertEquals(RemoteActionResult.RATE_LIMITED, last.getResult());
		assertEquals(12, executor.clickCount);
	}

	private static RemoteActionService service(RecordingExecutor executor)
	{
		return new RemoteActionService(executor, fixedClock());
	}

	private static Clock fixedClock()
	{
		return Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
	}

	private static RemoteAction action(
		String id,
		RemoteActionType type,
		long created,
		long expires,
		String pattern,
		int intensity,
		int duration,
		String message,
		boolean desktop,
		boolean chatbox)
	{
		return RemoteAction.forTest(
			id, type, created, expires, pattern, intensity, duration,
			message, desktop, chatbox
		);
	}

	private static final class RecordingExecutor implements RemoteActionExecutor
	{
		private int hapticCount;
		private int clickCount;
		private int stopCount;
		private String pattern;
		private String message;
		private boolean desktop;
		private boolean chatbox;

		@Override
		public void playHaptic(String patternSelection, int intensityPercent, int durationMillis)
		{
			hapticCount++;
			pattern = patternSelection;
		}

		@Override
		public void setRemoteLiveIntensity(int intensityPercent) { }

		@Override
		public void releaseRemoteLiveHaptic() { }

		@Override
		public void stopRemoteLiveHaptic() { }

		@Override
		public void playClick()
		{
			clickCount++;
		}

		@Override
		public void showMessage(String shown, boolean desktopNotification, boolean localChatboxMessage)
		{
			message = shown;
			desktop = desktopNotification;
			chatbox = localChatboxMessage;
		}

		@Override
		public void stopRemoteOutput()
		{
			stopCount++;
		}
	}
}
