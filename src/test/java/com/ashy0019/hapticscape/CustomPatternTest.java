package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CustomPatternTest
{
	@Test
	public void serializedCurveRoundTrips()
	{
		CustomPattern original = new CustomPattern(0, 17, 64, 100, 35, 0);

		assertEquals(original, CustomPattern.fromConfigValue(original.toConfigValue()));
	}

	@Test
	public void interpolationMakesDrawnCurvesSmooth()
	{
		CustomPattern pattern = new CustomPattern(0, 100);

		assertEquals(0.0, pattern.sampleAt(0.0), 0.0001);
		assertEquals(50.0, pattern.sampleAt(0.5), 0.0001);
		assertEquals(100.0, pattern.sampleAt(1.0), 0.0001);
	}

	@Test
	public void compiledPatternUsesExactDurationAndSafeIntensity()
	{
		HapticPattern pattern = new CustomPattern(0, 100, 0).createPattern(
			0.47,
			Duration.ofMillis(500)
		);
		long durationMillis = pattern.getSteps().stream()
			.map(HapticPattern.Step::getDuration)
			.mapToLong(Duration::toMillis)
			.sum();

		assertEquals(500, durationMillis);
		assertTrue(pattern.getSteps().stream().allMatch(step -> step.getIntensity() <= 0.47));
	}
}
