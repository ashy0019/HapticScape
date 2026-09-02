package com.ashy0019.hapticscape.device;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

public interface IntifaceService extends AutoCloseable
{
	void connect(URI serverUri);

	void disconnect();

	void pulse(double intensity, Duration duration);

	void playPattern(HapticPattern pattern);

	/**
	 * Updates a continuous, low-priority output such as music visualization.
	 * Finite patterns temporarily override this value and restore it when done.
	 */
	void setLiveIntensity(double intensity);

	void stopLiveOutput();

	void stopAll();

	ConnectionSnapshot getSnapshot();

	void setConnectionListener(Consumer<ConnectionSnapshot> listener);

	@Override
	void close();
}
