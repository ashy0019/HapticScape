package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureEndpoint;
import com.ashy0019.hapticscape.music.AudioCaptureEndpointCatalog;
import com.ashy0019.hapticscape.music.AudioCaptureSource;
import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureApplicationCatalog;
import com.ashy0019.hapticscape.music.AudioCaptureSourceFactory;

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

	/** Lists applications exposed by the ordinary Windows audio-session mixer. */
	public static AudioCaptureApplicationCatalog applicationCatalog()
	{
		return new WasapiAudioApplicationCatalog();
	}

	/** Creates both whole-output and per-application PCM Music Sync sources. */
	public static AudioCaptureSourceFactory factory()
	{
		return new AudioCaptureSourceFactory()
		{
			@Override
			public AudioCaptureSource create(AudioCaptureEndpoint endpoint)
			{
				return systemOutput(endpoint);
			}

			@Override
			public AudioCaptureSource createApplication(
				AudioCaptureApplication application)
			{
				return new WasapiApplicationLoopbackCapture(application);
			}
		};
	}
}
