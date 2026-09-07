package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.audio.HapticScapeSound;
import com.ashy0019.hapticscape.audio.SoundPlayer;
import java.util.Objects;
import net.runelite.client.audio.AudioPlayer;

/**
 * RuneLite-hosted implementation of HapticScape's neutral sound service.
 */
public final class RuneLiteSoundPlayer implements SoundPlayer
{
	private final AudioPlayer audioPlayer;

	public RuneLiteSoundPlayer(AudioPlayer audioPlayer)
	{
		this.audioPlayer = Objects.requireNonNull(audioPlayer);
	}

	@Override
	public void play(HapticScapeSound sound, float gainDb) throws Exception
	{
		audioPlayer.play(
			RuneLiteSoundPlayer.class,
			Objects.requireNonNull(sound).getResourcePath(),
			gainDb
		);
	}
}
