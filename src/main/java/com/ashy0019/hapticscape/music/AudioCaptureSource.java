package com.ashy0019.hapticscape.music;

public interface AudioCaptureSource extends AutoCloseable
{
	interface Listener
	{
		void onStarted(String description);

		void onSamples(float[] monoSamples, int sampleRate);

		void onError(String message, Throwable error);
	}

	void start(Listener listener);

	@Override
	void close();
}
