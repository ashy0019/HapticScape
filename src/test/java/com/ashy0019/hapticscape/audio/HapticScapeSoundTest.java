package com.ashy0019.hapticscape.audio;

import java.io.InputStream;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class HapticScapeSoundTest
{
	@Test
	public void everyBundledSoundResourceExists() throws Exception
	{
		for (HapticScapeSound sound : HapticScapeSound.values())
		{
			try (InputStream resource = HapticScapeSound.class.getResourceAsStream(
				sound.getResourcePath()
			))
			{
				assertNotNull(sound.name(), resource);
			}
		}
	}
}
