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
}
