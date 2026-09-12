package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureSource;

/** Desktop-host factory for audio capture sources used by HapticScape. */
public final class DesktopAudioCaptureSources
{
	private DesktopAudioCaptureSources()
	{
	}

	/** Returns a capture source for the desktop system-output stream. */
	public static AudioCaptureSource systemOutput()
	{
		return new WasapiLoopbackCapture();
	}
}
