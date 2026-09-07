package com.ashy0019.hapticscape.audio;

/**
 * Host-provided playback for one bundled HapticScape sound.
 */
@FunctionalInterface
public interface SoundPlayer
{
	void play(HapticScapeSound sound, float gainDb) throws Exception;
}
