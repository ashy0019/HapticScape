package com.ashy0019.hapticscape.music;

/** Opens a capture source for one selected local desktop output. */
@FunctionalInterface
public interface AudioCaptureSourceFactory
{
	AudioCaptureSource create(AudioCaptureEndpoint endpoint);
}
