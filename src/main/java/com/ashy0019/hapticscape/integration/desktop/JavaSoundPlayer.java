package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.audio.HapticScapeSound;
import com.ashy0019.hapticscape.audio.SoundPlayer;
import java.net.URL;
import java.util.Objects;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/** Desktop sound playback backed by the JDK's Java Sound API. */
public final class JavaSoundPlayer implements SoundPlayer
{
	@Override
	public void play(HapticScapeSound sound, float gainDb) throws Exception
	{
		HapticScapeSound cue = Objects.requireNonNull(sound, "sound");
		URL resource = JavaSoundPlayer.class.getResource(cue.getResourcePath());
		if (resource == null)
		{
			throw new IllegalArgumentException("Missing bundled sound: " + cue.getResourcePath());
		}

		Clip clip = AudioSystem.getClip();
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(resource))
		{
			clip.open(stream);
		}
		if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), gainDb)));
		}
		clip.addLineListener(event ->
		{
			if (event.getType() == LineEvent.Type.STOP && clip.isOpen())
			{
				clip.close();
			}
		});
		clip.start();
	}
}
