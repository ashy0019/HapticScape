package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
	public void ascendingPatternBuildsThreeIncreasingPulses()
	{
		HapticPattern pattern = HapticPatternPreset.ASCENDING.createPattern(
			0.8,
			Duration.ofMillis(500)
		);

		assertEquals(5, pattern.getSteps().size());
		assertEquals(0.28, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.0, pattern.getSteps().get(1).getIntensity(), 0.0001);
		assertEquals(0.52, pattern.getSteps().get(2).getIntensity(), 0.0001);
		assertEquals(0.0, pattern.getSteps().get(3).getIntensity(), 0.0001);
		assertEquals(0.8, pattern.getSteps().get(4).getIntensity(), 0.0001);
	}

	@Test
	public void unknownSavedPresetFallsBackToSinglePulse()
	{
		assertEquals(HapticPatternPreset.SINGLE, HapticPatternPreset.fromConfigValue(null));
		assertEquals(HapticPatternPreset.SINGLE, HapticPatternPreset.fromConfigValue("removed-preset"));
		assertEquals(HapticPatternPreset.TRIPLE, HapticPatternPreset.fromConfigValue("triple"));
	}
}
