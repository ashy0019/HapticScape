package com.ashy0019.hapticscape.clicker;

import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClickerResourceTest
{
	@Test
	public void bundledClickIsSupportedPcmWave() throws Exception
	{
		InputStream resource = ClickerResourceTest.class.getResourceAsStream("/clicker.wav");
		assertNotNull(resource);

		try (InputStream input = resource;
			 AudioInputStream audio = AudioSystem.getAudioInputStream(input))
		{
			AudioFormat format = audio.getFormat();
			assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
			assertEquals(44_100.0f, format.getSampleRate(), 0.1f);
			assertEquals(16, format.getSampleSizeInBits());
			assertEquals(1, format.getChannels());
			assertTrue(audio.getFrameLength() > 0);
		}
	}
}
