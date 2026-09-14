package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureEndpoint;
import com.ashy0019.hapticscape.music.AudioCaptureEndpointCatalog;
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

	/** Returns a capture source for the selected Windows render endpoint. */
	public static AudioCaptureSource systemOutput(AudioCaptureEndpoint endpoint)
	{
		return new WasapiLoopbackCapture(endpoint);
	}

	/** Lists active Windows render endpoints without applying vendor-specific rules. */
	public static AudioCaptureEndpointCatalog endpointCatalog()
	{
		return new WasapiAudioEndpointCatalog();
	}
}
