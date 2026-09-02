package com.ashy0019.hapticscape.clicker;

import java.util.Objects;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the bundled mechanical click through RuneLite's audio player.
 */
public final class AudioPlayerClickPlayback implements ClickPlayback
{
	private static final String CLICK_RESOURCE = "/clicker.wav";

	private final AudioPlayer audioPlayer;

	public AudioPlayerClickPlayback(AudioPlayer audioPlayer)
	{
		this.audioPlayer = Objects.requireNonNull(audioPlayer);
	}

	@Override
	public void play(float gainDb) throws Exception
	{
		audioPlayer.play(AudioPlayerClickPlayback.class, CLICK_RESOURCE, gainDb);
	}
}
