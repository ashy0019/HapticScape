package com.ashy0019.hapticscape.device;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

public interface IntifaceService extends AutoCloseable
{
	void connect(URI serverUri);

	void disconnect();

	void pulse(double intensity, Duration duration);

	void play(HapticRequest request);

	/**
	 * Updates a continuous, low-priority output such as music visualization.
	 * Finite patterns temporarily override this value and restore it when done.
	 */
	void setLiveIntensity(double intensity);

	void stopLiveOutput();

	/** Updates the participant's higher-priority Remote Control live lane. */
	void setRemoteLiveIntensity(double intensity);

	/** Smoothly returns the Remote Control live lane to zero. */
	void releaseRemoteLiveOutput(Duration decayDuration);

	/** Immediately disables only the Remote Control live lane. */
	void stopRemoteLiveOutput();

	void stopAll();

	ConnectionSnapshot getSnapshot();

	void setConnectionListener(Consumer<ConnectionSnapshot> listener);

	@Override
	void close();
}
