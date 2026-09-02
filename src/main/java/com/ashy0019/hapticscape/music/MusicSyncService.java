package com.ashy0019.hapticscape.music;

import com.ashy0019.hapticscape.device.IntifaceService;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MusicSyncService implements AutoCloseable
{
	private static final long OUTPUT_INTERVAL_NANOS = 50_000_000L;

	private final IntifaceService intifaceService;
	private final Supplier<AudioCaptureSource> sourceFactory;
	private volatile Consumer<MusicSyncSnapshot> listener = ignored -> { };
	private volatile MusicSyncSettings settings;
	private volatile MusicSyncSnapshot snapshot = MusicSyncSnapshot.disabled();
	private volatile AudioCaptureSource source;
	private volatile MusicSignalAnalyzer analyzer;
	private volatile long generation;
	private long lastOutputNanos;
	private double lastOutput = -1.0;

	public MusicSyncService(
		IntifaceService intifaceService,
		Supplier<AudioCaptureSource> sourceFactory,
		MusicSyncSettings initialSettings)
	{
		this.intifaceService = Objects.requireNonNull(intifaceService, "intifaceService");
		this.sourceFactory = Objects.requireNonNull(sourceFactory, "sourceFactory");
		this.settings = Objects.requireNonNull(initialSettings, "initialSettings");
	}

	public synchronized void updateSettings(MusicSyncSettings next)
	{
		settings = Objects.requireNonNull(next, "next");
		if (!next.isEnabled())
		{
			stopCapture();
			publish(MusicSyncSnapshot.disabled());
			return;
		}

		if (analyzer != null)
		{
			analyzer.setResponse(next.getResponse());
			return;
		}
		startCapture();
	}

	public synchronized void stopNow()
	{
		settings = settings.withEnabled(false);
		stopCapture();
		publish(MusicSyncSnapshot.disabled());
	}

	public MusicSyncSettings getSettings()
	{
		return settings;
	}

	public MusicSyncSnapshot getSnapshot()
	{
		return snapshot;
	}

	public void setListener(Consumer<MusicSyncSnapshot> nextListener)
	{
		listener = Objects.requireNonNull(nextListener, "nextListener");
		listener.accept(snapshot);
	}

	private void startCapture()
	{
		long currentGeneration = ++generation;
		lastOutputNanos = 0;
		lastOutput = -1.0;
		analyzer = new MusicSignalAnalyzer(
			level -> handleAnalyzedLevel(currentGeneration, level)
		);
		analyzer.setResponse(settings.getResponse());
		try
		{
			source = sourceFactory.get();
			publish(new MusicSyncSnapshot(
				MusicSyncSnapshot.State.STARTING,
				"Opening Windows system audio",
				0
			));
			source.start(new AudioCaptureSource.Listener()
			{
				@Override
				public void onStarted(String description)
				{
					if (isCurrent(currentGeneration))
					{
						publish(new MusicSyncSnapshot(
							MusicSyncSnapshot.State.RUNNING,
							description,
							0
						));
					}
				}

				@Override
				public void onSamples(float[] monoSamples, int sampleRate)
				{
					MusicSignalAnalyzer currentAnalyzer;
					synchronized (MusicSyncService.this)
					{
						currentAnalyzer = isCurrent(currentGeneration) ? analyzer : null;
					}
					if (currentAnalyzer != null)
					{
						currentAnalyzer.accept(monoSamples, sampleRate);
					}
				}

				@Override
				public void onError(String message, Throwable error)
				{
					handleCaptureError(currentGeneration, message, error);
				}
			});
		}
		catch (RuntimeException e)
		{
			handleCaptureError(currentGeneration, "Unable to start system audio capture", e);
		}
	}

	private void handleAnalyzedLevel(long currentGeneration, double normalizedLevel)
	{
		MusicSyncSettings currentSettings = settings;
		if (!isCurrent(currentGeneration) || !currentSettings.isEnabled())
		{
			return;
		}
		double output = currentSettings.mapIntensity(normalizedLevel);
		long now = System.nanoTime();
		boolean silenceTransition = output == 0.0 && lastOutput > 0.0;
		if (!silenceTransition && now - lastOutputNanos < OUTPUT_INTERVAL_NANOS)
		{
			return;
		}
		lastOutputNanos = now;
		lastOutput = output;
		intifaceService.setLiveIntensity(output);

		MusicSyncSnapshot currentSnapshot = snapshot;
		if (currentSnapshot.getState() == MusicSyncSnapshot.State.RUNNING)
		{
			publish(new MusicSyncSnapshot(
				MusicSyncSnapshot.State.RUNNING,
				currentSnapshot.getMessage(),
				(int) Math.round(output * 100.0)
			));
		}
	}

	private synchronized void handleCaptureError(
		long currentGeneration,
		String message,
		Throwable error)
	{
		if (!isCurrent(currentGeneration))
		{
			return;
		}
		stopCapture();
		publish(new MusicSyncSnapshot(MusicSyncSnapshot.State.ERROR, message, 0));
	}

	private boolean isCurrent(long currentGeneration)
	{
		return currentGeneration == generation && analyzer != null;
	}

	private void stopCapture()
	{
		generation++;
		AudioCaptureSource currentSource = source;
		source = null;
		analyzer = null;
		if (currentSource != null)
		{
			currentSource.close();
		}
		intifaceService.stopLiveOutput();
		lastOutput = -1.0;
	}

	private void publish(MusicSyncSnapshot next)
	{
		snapshot = next;
		listener.accept(next);
	}

	@Override
	public synchronized void close()
	{
		stopCapture();
		listener = ignored -> { };
	}
}
