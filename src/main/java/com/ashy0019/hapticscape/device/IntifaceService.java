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

	void stopAll();

	ConnectionSnapshot getSnapshot();

	void setConnectionListener(Consumer<ConnectionSnapshot> listener);

	@Override
	void close();
}
