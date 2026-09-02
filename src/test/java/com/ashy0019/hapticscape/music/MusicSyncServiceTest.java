package com.ashy0019.hapticscape.music;

import com.ashy0019.hapticscape.device.ConnectionSnapshot;
import com.ashy0019.hapticscape.device.HapticPattern;
import com.ashy0019.hapticscape.device.IntifaceService;
import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MusicSyncServiceTest
{
	@Test
	public void capturesAnalyzesAndStopsLiveOutput()
	{
		FakeIntifaceService intiface = new FakeIntifaceService();
		FakeCaptureSource capture = new FakeCaptureSource();
		MusicSyncService service = new MusicSyncService(
			intiface,
			() -> capture,
			settings(false)
		);

		service.updateSettings(settings(true));
		assertTrue(capture.started);
		assertEquals(MusicSyncSnapshot.State.RUNNING, service.getSnapshot().getState());

		float[] bass = new float[4_096];
		for (int index = 0; index < bass.length; index++)
		{
			bass[index] = (float) (0.5 * Math.sin(2.0 * Math.PI * 110.0 * index / 48_000.0));
		}
		capture.emit(bass, 48_000);
		assertTrue(intiface.liveIntensity > 0.0);

		service.stopNow();
		assertTrue(capture.closed);
		assertTrue(intiface.liveStopped);
		assertFalse(service.getSettings().isEnabled());
		assertEquals(MusicSyncSnapshot.State.DISABLED, service.getSnapshot().getState());
	}

	@Test
	public void captureFailureBecomesVisibleState()
	{
		FakeIntifaceService intiface = new FakeIntifaceService();
		FakeCaptureSource capture = new FakeCaptureSource();
		MusicSyncService service = new MusicSyncService(
			intiface,
			() -> capture,
			settings(false)
		);

		service.updateSettings(settings(true));
		capture.fail("No output device");

		assertEquals(MusicSyncSnapshot.State.ERROR, service.getSnapshot().getState());
		assertEquals("No output device", service.getSnapshot().getMessage());
		assertTrue(intiface.liveStopped);
	}

	private static MusicSyncSettings settings(boolean enabled)
	{
		return new MusicSyncSettings(enabled, MusicResponse.RHYTHMIC, 100, 0, 60);
	}

	private static final class FakeCaptureSource implements AudioCaptureSource
	{
		private Listener listener;
		private boolean started;
		private boolean closed;

		@Override
		public void start(Listener listener)
		{
			this.listener = listener;
			started = true;
			listener.onStarted("Listening");
		}

		private void emit(float[] samples, int sampleRate)
		{
			listener.onSamples(samples, sampleRate, 1.0);
		}

		private void fail(String message)
		{
			listener.onError(message, null);
		}

		@Override
		public void close()
		{
			closed = true;
		}
	}

	private static final class FakeIntifaceService implements IntifaceService
	{
		private double liveIntensity;
		private boolean liveStopped;

		@Override
		public void setLiveIntensity(double intensity)
		{
			liveIntensity = intensity;
		}

		@Override
		public void stopLiveOutput()
		{
			liveStopped = true;
		}

		@Override public void connect(URI serverUri) { }
		@Override public void disconnect() { }
		@Override public void pulse(double intensity, Duration duration) { }
		@Override public void playPattern(HapticPattern pattern) { }
		@Override public void stopAll() { }
		@Override public ConnectionSnapshot getSnapshot() { return ConnectionSnapshot.disconnected(); }
		@Override public void setConnectionListener(Consumer<ConnectionSnapshot> listener) { }
		@Override public void close() { }
	}
}
