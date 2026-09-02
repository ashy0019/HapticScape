package com.ashy0019.hapticscape.music;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MusicSignalAnalyzerTest
{
	@Test
	public void silenceProducesNoOutput()
	{
		List<Double> levels = new ArrayList<>();
		MusicSignalAnalyzer analyzer = new MusicSignalAnalyzer(levels::add);

		analyzer.accept(new float[4_096], 48_000);

		assertFalse(levels.isEmpty());
		assertTrue(levels.stream().allMatch(level -> level == 0.0));
	}

	@Test
	public void bassSignalProducesBoundedHapticLevel()
	{
		List<Double> levels = new ArrayList<>();
		MusicSignalAnalyzer analyzer = new MusicSignalAnalyzer(levels::add);
		float[] bass = new float[4_096];
		for (int index = 0; index < bass.length; index++)
		{
			bass[index] = (float) (0.5 * Math.sin(2.0 * Math.PI * 110.0 * index / 48_000));
		}

		analyzer.accept(bass, 48_000);

		assertTrue(levels.stream().anyMatch(level -> level > 0.05));
		assertTrue(levels.stream().allMatch(level -> level >= 0.0 && level <= 1.0));
	}

	@Test
	public void actualVolumeMakesQuietMusicWeaker()
	{
		double loud = peakBassLevel(0.40);
		double quiet = peakBassLevel(0.02);

		assertTrue(loud > quiet * 3.0);
	}

	@Test
	public void windowsMasterVolumeScalesDetectedBeat()
	{
		double fullVolume = peakBassLevel(0.40, 1.0);
		double quarterVolume = peakBassLevel(0.40, 0.25);

		assertTrue(fullVolume > quarterVolume * 3.0);
	}

	@Test
	public void windowsMuteImmediatelyProducesZero()
	{
		List<Double> levels = new ArrayList<>();
		MusicSignalAnalyzer analyzer = new MusicSignalAnalyzer(levels::add);
		analyzer.setOutputVolume(0.0);

		analyzer.accept(new float[512], 48_000);

		assertTrue(levels.stream().allMatch(level -> level == 0.0));
	}

	private static double peakBassLevel(double amplitude)
	{
		return peakBassLevel(amplitude, 1.0);
	}

	private static double peakBassLevel(double amplitude, double outputVolume)
	{
		List<Double> levels = new ArrayList<>();
		MusicSignalAnalyzer analyzer = new MusicSignalAnalyzer(levels::add);
		analyzer.setOutputVolume(outputVolume);
		float[] bass = new float[8_192];
		for (int index = 0; index < bass.length; index++)
		{
			bass[index] = (float) (amplitude
				* Math.sin(2.0 * Math.PI * 110.0 * index / 48_000));
		}
		analyzer.accept(bass, 48_000);
		return levels.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
	}
}
