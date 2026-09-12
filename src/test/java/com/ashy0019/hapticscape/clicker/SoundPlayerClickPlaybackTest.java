package com.ashy0019.hapticscape.clicker;

import com.ashy0019.hapticscape.audio.HapticScapeSound;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SoundPlayerClickPlaybackTest
{
	@Test
	public void clickUsesNeutralClickSoundCue() throws Exception
	{
		AtomicReference<HapticScapeSound> sound = new AtomicReference<>();
		AtomicReference<Float> gain = new AtomicReference<>();
		SoundPlayerClickPlayback playback = new SoundPlayerClickPlayback((requestedSound, gainDb) ->
		{
			sound.set(requestedSound);
			gain.set(gainDb);
		});

		playback.play(-3.5f);

		assertEquals(HapticScapeSound.CLICK, sound.get());
		assertEquals(-3.5f, gain.get(), 0.0001f);
	}
}
