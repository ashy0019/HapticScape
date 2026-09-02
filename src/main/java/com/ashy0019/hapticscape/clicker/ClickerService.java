package com.ashy0019.hapticscape.clicker;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Independent, immediate click playback channel.
 *
 * <p>The executor deliberately has no task queue. If both playback slots are
 * occupied, a new click is dropped instead of being played late.</p>
 */
@Slf4j
public final class ClickerService implements AutoCloseable
{
	static final int MAXIMUM_CONCURRENT_CLICKS = 2;

	private final ClickPlayback playback;
	private final ThreadPoolExecutor executor;
	private volatile ClickerSettings settings;
	private volatile boolean closed;

	public ClickerService(ClickPlayback playback, ClickerSettings initialSettings)
	{
		this.playback = Objects.requireNonNull(playback);
		this.settings = Objects.requireNonNull(initialSettings);
		AtomicInteger threadNumber = new AtomicInteger();
		executor = new ThreadPoolExecutor(
			0,
			MAXIMUM_CONCURRENT_CLICKS,
			10,
			TimeUnit.SECONDS,
			new SynchronousQueue<>(),
			task ->
			{
				Thread thread = new Thread(
					task,
					"hapticscape-clicker-" + threadNumber.incrementAndGet()
				);
				thread.setDaemon(true);
				return thread;
			},
			new ThreadPoolExecutor.AbortPolicy()
		);
	}

	public void updateSettings(ClickerSettings updatedSettings)
	{
		settings = Objects.requireNonNull(updatedSettings);
	}

	public void click()
	{
		ClickerSettings current = settings;
		if (closed || !current.isEnabled() || current.getVolumePercent() == 0)
		{
			return;
		}

		try
		{
			executor.execute(() -> playSafely(current.getGainDb()));
		}
		catch (RejectedExecutionException ignored)
		{
			log.debug("Dropping click because both playback slots are busy");
		}
	}

	private void playSafely(float gainDb)
	{
		try
		{
			playback.play(gainDb);
		}
		catch (Exception e)
		{
			log.warn("Unable to play clicker sound", e);
		}
	}

	@Override
	public void close()
	{
		closed = true;
		executor.shutdownNow();
	}
}
