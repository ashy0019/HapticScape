package com.ashy0019.hapticscape.audio;

/**
 * Bundled sounds that HapticScape can request from its host audio service.
 */
public enum HapticScapeSound
{
	CLICK("/clicker.wav"),
	LEVEL_99_CHEER("/level99-cheer.wav"),
	ROGUE_UNLOCK_STING("/rogue/rogue-unlock.wav");

	private final String resourcePath;

	HapticScapeSound(String resourcePath)
	{
		this.resourcePath = resourcePath;
	}

	public String getResourcePath()
	{
		return resourcePath;
	}
}
