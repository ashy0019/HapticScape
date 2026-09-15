package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HapticPatternPresetTest
{
	@Test
	public void everyPresetUsesTheConfiguredTotalDuration()
	{
		for (HapticPatternPreset preset : HapticPatternPreset.values())
		{
			HapticPattern pattern = preset.createPattern(0.8, Duration.ofMillis(500));
			long totalMillis = pattern.getSteps().stream()
				.map(HapticPattern.Step::getDuration)
				.mapToLong(Duration::toMillis)
				.sum();

			assertEquals(preset.toString(), 500, totalMillis);
		}
	}

	@Test
	public void ascendingPatternBuildsContinuousNonlinearRamp()
	{
		HapticPattern pattern = HapticPatternPreset.ASCENDING.createPattern(
			0.8,
			Duration.ofMillis(500)
		);

		assertEquals(12, pattern.getSteps().size());
		assertEquals(0.272, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.8, pattern.getSteps().get(11).getIntensity(), 0.0001);
		for (int index = 1; index < pattern.getSteps().size(); index++)
		{
			assertTrue(pattern.getSteps().get(index).getIntensity()
				> pattern.getSteps().get(index - 1).getIntensity());
		}
	}

	@Test
	public void descendingPatternBuildsContinuousNonlinearRamp()
	{
		HapticPattern pattern = HapticPatternPreset.DESCENDING.createPattern(
			0.8,
			Duration.ofMillis(500)
		);

		assertEquals(12, pattern.getSteps().size());
		assertEquals(0.8, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.272, pattern.getSteps().get(11).getIntensity(), 0.0001);
		for (int index = 1; index < pattern.getSteps().size(); index++)
		{
			assertTrue(pattern.getSteps().get(index).getIntensity()
				< pattern.getSteps().get(index - 1).getIntensity());
		}
	}

	@Test
	public void unknownSavedPresetFallsBackToSinglePulse()
	{
		assertEquals(HapticPatternPreset.SINGLE, HapticPatternPreset.fromConfigValue(null));
		assertEquals(HapticPatternPreset.SINGLE, HapticPatternPreset.fromConfigValue("removed-preset"));
		assertEquals(HapticPatternPreset.TRIPLE, HapticPatternPreset.fromConfigValue("triple"));
	}
}
