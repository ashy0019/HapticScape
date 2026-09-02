package com.ashy0019.hapticscape.music;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RealFftTest
{
	@Test
	public void identifiesDominantFrequencyBin()
	{
		int size = 2_048;
		int sampleRate = 48_000;
		double frequency = 1_000.0;
		float[] samples = sineWave(size, sampleRate, frequency, 0.75);
		double[] magnitudes = new RealFft(size).magnitudes(samples);

		int peak = 1;
		for (int index = 2; index < magnitudes.length; index++)
		{
			if (magnitudes[index] > magnitudes[peak])
			{
				peak = index;
			}
		}

		double detectedFrequency = (double) peak * sampleRate / size;
		assertEquals(frequency, detectedFrequency, (double) sampleRate / size);
		assertTrue(magnitudes[peak] > 0.5);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonPowerOfTwoSize()
	{
		new RealFft(1_000);
	}

	private static float[] sineWave(
		int size,
		int sampleRate,
		double frequency,
		double amplitude)
	{
		float[] samples = new float[size];
		for (int index = 0; index < size; index++)
		{
			samples[index] = (float) (
				amplitude * Math.sin(2.0 * Math.PI * frequency * index / sampleRate)
			);
		}
		return samples;
	}
}
