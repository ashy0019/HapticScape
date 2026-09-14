package com.ashy0019.hapticscape.music;

/** Opens a capture source for one selected local desktop output. */
@FunctionalInterface
public interface AudioCaptureSourceFactory
{
	AudioCaptureSource create(AudioCaptureEndpoint endpoint);

	default AudioCaptureSource createApplication(AudioCaptureApplication application)
	{
		throw new UnsupportedOperationException("Application audio capture is unavailable");
	}
}
