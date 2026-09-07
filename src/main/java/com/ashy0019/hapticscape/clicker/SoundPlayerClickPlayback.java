package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.audio.HapticScapeSound;
import com.ashy0019.hapticscape.audio.SoundPlayer;
import java.util.Objects;

/**
 * Routes click playback through HapticScape's host-provided sound service.
 */
public final class SoundPlayerClickPlayback implements ClickPlayback
{
	private final SoundPlayer soundPlayer;

	public SoundPlayerClickPlayback(SoundPlayer soundPlayer)
	{
		this.soundPlayer = Objects.requireNonNull(soundPlayer);
	}

	@Override
	public void play(float gainDb) throws Exception
	{
		soundPlayer.play(HapticScapeSound.CLICK, gainDb);
	}
}
