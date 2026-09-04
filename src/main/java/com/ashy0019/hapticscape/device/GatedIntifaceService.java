package com.ashy0019.hapticscape.device;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Safety gate around an ordinary IntifaceService.
 *
 * <p>Pausing output never disconnects Intiface. Connection management and
 * device discovery remain local, while all new haptic output is suppressed
 * until the local participant resumes it.</p>
 */
public final class GatedIntifaceService implements IntifaceService
{
	private final IntifaceService delegate;
	private final AtomicBoolean outputPaused = new AtomicBoolean();

	public GatedIntifaceService(IntifaceService delegate)
	{
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	public boolean isOutputPaused()
	{
		return outputPaused.get();
	}

	public void setOutputPaused(boolean paused)
	{
		boolean changed = outputPaused.getAndSet(paused) != paused;
		if (paused && changed)
		{
			delegate.stopAll();
			delegate.stopLiveOutput();
		}
	}

	@Override
	public void connect(URI serverUri)
	{
		delegate.connect(serverUri);
	}

	@Override
	public void disconnect()
	{
		delegate.disconnect();
	}

	@Override
	public void pulse(double intensity, Duration duration)
	{
		if (!outputPaused.get())
		{
			delegate.pulse(intensity, duration);
		}
	}

	@Override
	public void play(HapticRequest request)
	{
		if (!outputPaused.get())
		{
			delegate.play(request);
		}
	}

	@Override
	public void setLiveIntensity(double intensity)
	{
		if (!outputPaused.get())
		{
			delegate.setLiveIntensity(intensity);
		}
	}

	@Override
	public void stopLiveOutput()
	{
		delegate.stopLiveOutput();
	}

	@Override
	public void setRemoteLiveIntensity(double intensity)
	{
		if (!outputPaused.get())
		{
			delegate.setRemoteLiveIntensity(intensity);
		}
	}

	@Override
	public void releaseRemoteLiveOutput(Duration decayDuration)
	{
		delegate.releaseRemoteLiveOutput(decayDuration);
	}

	@Override
	public void stopRemoteLiveOutput()
	{
		delegate.stopRemoteLiveOutput();
	}

	@Override
	public void stopAll()
	{
		delegate.stopAll();
	}

	@Override
	public ConnectionSnapshot getSnapshot()
	{
		return delegate.getSnapshot();
	}

	@Override
	public void setConnectionListener(Consumer<ConnectionSnapshot> listener)
	{
		delegate.setConnectionListener(listener);
	}

	@Override
	public void close()
	{
		delegate.close();
	}
}
