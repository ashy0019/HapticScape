package com.ashy0019.hapticscape.music;

import com.sun.jna.Memory;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class WasapiLoopbackCaptureTest
{
	@Test
	public void convertsStereoFloatMixToMono()
	{
		Memory format = waveFormat(3, 2, 48_000, 8, 32);
		Memory samples = new Memory(16);
		samples.setFloat(0, 0.75f);
		samples.setFloat(4, 0.25f);
		samples.setFloat(8, -0.5f);
		samples.setFloat(12, 0.25f);

		float[] mono = WasapiLoopbackCapture.AudioFormat.read(format)
			.toMono(samples, 2);

		assertArrayEquals(new float[] {0.5f, -0.125f}, mono, 0.0001f);
	}

	@Test
	public void convertsStereoPcm16MixToMono()
	{
		Memory format = waveFormat(1, 2, 44_100, 4, 16);
		Memory samples = new Memory(8);
		samples.setShort(0, (short) 16_384);
		samples.setShort(2, (short) 8_192);
		samples.setShort(4, (short) -16_384);
		samples.setShort(6, (short) 0);

		float[] mono = WasapiLoopbackCapture.AudioFormat.read(format)
			.toMono(samples, 2);

		assertArrayEquals(new float[] {0.375f, -0.25f}, mono, 0.0001f);
	}

	private static Memory waveFormat(
		int tag,
		int channels,
		int sampleRate,
		int blockAlign,
		int bitsPerSample)
	{
		Memory format = new Memory(18);
		format.clear();
		format.setShort(0, (short) tag);
		format.setShort(2, (short) channels);
		format.setInt(4, sampleRate);
		format.setInt(8, sampleRate * blockAlign);
		format.setShort(12, (short) blockAlign);
		format.setShort(14, (short) bitsPerSample);
		format.setShort(16, (short) 0);
		return format;
	}
}
