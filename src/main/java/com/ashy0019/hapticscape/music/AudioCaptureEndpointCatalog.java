package com.ashy0019.hapticscape.music;

import java.util.Collections;
import java.util.List;

/** Lists local desktop outputs available for Music Sync capture. */
@FunctionalInterface
public interface AudioCaptureEndpointCatalog
{
	List<AudioCaptureEndpoint> listActiveEndpoints();

	static AudioCaptureEndpointCatalog systemDefaultOnly()
	{
		return () -> Collections.singletonList(AudioCaptureEndpoint.systemDefault());
	}
}
