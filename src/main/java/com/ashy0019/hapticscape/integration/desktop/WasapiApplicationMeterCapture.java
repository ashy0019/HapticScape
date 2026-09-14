package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureSource;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Follows one application's local peak level as exposed by Windows Volume Mixer. */
final class WasapiApplicationMeterCapture implements AudioCaptureSource
{
	private static final long SESSION_REFRESH_NANOS = 2_000_000_000L;
	private static final long METER_INTERVAL_MILLIS = 50L;

	private final AudioCaptureApplication application;
	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Thread captureThread;

	WasapiApplicationMeterCapture(AudioCaptureApplication application)
	{
		this.application = Objects.requireNonNull(application, "application");
	}

	@Override
	public void start(Listener listener)
	{
		Objects.requireNonNull(listener, "listener");
		if (!Platform.isWindows())
		{
			throw new UnsupportedOperationException("Application audio capture requires Windows");
		}
		if (!running.compareAndSet(false, true))
		{
			throw new IllegalStateException("Application audio capture is already running");
		}
		Thread thread = new Thread(() -> capture(listener), "hapticscape-app-audio");
		thread.setDaemon(true);
		captureThread = thread;
		thread.start();
	}

	private void capture(Listener listener)
	{
		List<WasapiApplicationSessions.SessionMeter> meters = new ArrayList<>();
		boolean comInitialized = false;
		try
		{
			HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(
				Pointer.NULL,
				Ole32.COINIT_MULTITHREADED
			);
			COMUtils.checkRC(initialized);
			comInitialized = true;
			listener.onStarted("Listening to " + application.getDisplayName());
			long nextRefresh = 0L;
			while (running.get())
			{
				long now = System.nanoTime();
				if (now >= nextRefresh)
				{
					closeMeters(meters);
					meters = WasapiApplicationSessions.openMeters(application.getId());
					nextRefresh = now + SESSION_REFRESH_NANOS;
				}

				double peak = 0.0;
				for (WasapiApplicationSessions.SessionMeter meter : meters)
				{
					try
					{
						peak = Math.max(peak, meter.peak());
					}
					catch (RuntimeException ignored)
					{
						// The periodic session refresh removes expired mixer sessions.
					}
				}
				listener.onLevel(peak);
				try
				{
					Thread.sleep(METER_INTERVAL_MILLIS);
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		catch (Throwable failure)
		{
			if (running.get())
			{
				listener.onError("Windows application audio monitoring failed", failure);
			}
		}
		finally
		{
			running.set(false);
			closeMeters(meters);
			if (comInitialized)
			{
				Ole32.INSTANCE.CoUninitialize();
			}
			captureThread = null;
		}
	}

	private static void closeMeters(
		List<WasapiApplicationSessions.SessionMeter> meters)
	{
		for (WasapiApplicationSessions.SessionMeter meter : meters)
		{
			meter.close();
		}
		meters.clear();
	}

	@Override
	public void close()
	{
		running.set(false);
		Thread thread = captureThread;
		if (thread != null)
		{
			thread.interrupt();
		}
	}
}
