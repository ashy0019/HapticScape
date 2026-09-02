package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Level99CeremonyTest
{
	@Test
	public void ceremonyContainsExactlyNinetyNineNonzeroBeats()
	{
		long nonzeroBeats = Level99Ceremony.pattern().getSteps().stream()
			.filter(step -> step.getIntensity() > 0.0)
			.count();

		assertEquals(99, nonzeroBeats);
	}

	@Test
	public void ceremonyRepeatsEightShortBeatsAndOneLongBeatElevenTimes()
	{
		List<HapticPattern.Step> steps = Level99Ceremony.pattern().getSteps();
		assertEquals(198, steps.size());

		for (int phrase = 0; phrase < 11; phrase++)
		{
			int start = phrase * 18;
			for (int beat = 0; beat < 8; beat++)
			{
				assertStep(steps.get(start + beat * 2), 0.60, 55);
				assertStep(steps.get(start + beat * 2 + 1), 0.0, 35);
			}
			assertStep(steps.get(start + 16), 1.0, 260);
			assertStep(steps.get(start + 17), 0.0, 120);
		}
	}

	@Test
	public void ceremonyLastsTwelvePointOneSeconds()
	{
		assertEquals(Duration.ofMillis(1_100).toNanos(), Level99Ceremony.phraseDurationNanos());
		assertEquals(Duration.ofMillis(12_100).toNanos(), Level99Ceremony.totalDurationNanos());
	}

	@Test
	public void packagedCheerIsReadablePcmWave() throws Exception
	{
		InputStream resource = Level99Ceremony.class.getResourceAsStream("/level99-cheer.wav");
		assertNotNull(resource);

		try (BufferedInputStream buffered = new BufferedInputStream(resource);
			 AudioInputStream audio = AudioSystem.getAudioInputStream(buffered))
		{
			assertEquals(1, audio.getFormat().getChannels());
			assertEquals(22_050.0f, audio.getFormat().getSampleRate(), 0.1f);
			assertEquals(16, audio.getFormat().getSampleSizeInBits());
			assertTrue(audio.getFrameLength() > 0);
		}
	}

	private static void assertStep(HapticPattern.Step step, double intensity, long durationMillis)
	{
		assertEquals(intensity, step.getIntensity(), 0.0001);
		assertEquals(Duration.ofMillis(durationMillis), step.getDuration());
	}
}
