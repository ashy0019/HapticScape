package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HapticPatternSelectionTest
{
	@Test
	public void existingPresetConfigNamesRemainCompatible()
	{
		assertEquals(
			HapticPatternSelection.TRIPLE,
			HapticPatternSelection.fromConfigValue("triple")
		);
	}

	@Test
	public void fixedCustomSelectionNamesMigrateToStableIds()
	{
		assertEquals(
			HapticPatternSelection.custom(2),
			HapticPatternSelection.fromConfigValue("CUSTOM_II")
		);
	}

	@Test
	public void customSelectionsResolveCurrentSavedCurve()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.addBlankPattern()
			.withPattern(2, new CustomPattern(100, 0), 100, 3);
		HapticPattern pattern = HapticPatternSelection.custom(2).createPattern(
			library,
			0.5,
			Duration.ofMillis(999)
		);
		long durationMillis = pattern.getSteps().stream()
			.map(HapticPattern.Step::getDuration)
			.mapToLong(Duration::toMillis)
			.sum();

		assertEquals(6, pattern.getSteps().size());
		assertEquals(1.0, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.0, pattern.getSteps().get(1).getIntensity(), 0.0001);
		assertEquals(1.0, pattern.getSteps().get(2).getIntensity(), 0.0001);
		assertEquals(300, durationMillis);
	}

	@Test
	public void customSelectionIgnoresProfileIntensityAndDuration()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withPattern(1, new CustomPattern(80, 20), 150, 2);

		HapticPattern pattern = HapticPatternSelection.custom(1).createPattern(
			library,
			0.10,
			Duration.ofMillis(9_999)
		);

		assertEquals(0.80, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.20, pattern.getSteps().get(1).getIntensity(), 0.0001);
		assertEquals(300, pattern.getSteps().stream()
			.map(HapticPattern.Step::getDuration)
			.mapToLong(Duration::toMillis)
			.sum());
	}

	@Test
	public void customPatternSupportsSeventyTwoBeats()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withPattern(1, new CustomPattern(100, 0), 50, 72);

		HapticPattern pattern = HapticPatternSelection.custom(1).createPattern(
			library,
			0.25,
			Duration.ofMillis(500)
		);

		assertEquals(72, pattern.getSteps().size());
		assertEquals(3_600, pattern.getSteps().stream()
			.map(HapticPattern.Step::getDuration)
			.mapToLong(Duration::toMillis)
			.sum());
	}

	@Test
	public void renamedPatternsAppearInPatternSelectors()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withName(1, "Goblin drum");

		assertEquals(
			"Goblin drum",
			HapticPatternSelection.custom(1).getDisplayName(library)
		);
	}

	@Test
	public void deletedCustomSelectionFallsBackToSinglePulse()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults();

		assertEquals(
			HapticPatternSelection.SINGLE,
			HapticPatternSelection.custom(99).resolveAgainst(library)
		);
	}

	@Test
	public void descendingPatternIsBuiltInAndFallsInIntensity()
	{
		HapticPattern pattern = HapticPatternSelection.DESCENDING.createPattern(
			CustomPatternLibrary.defaults(),
			0.8,
			Duration.ofMillis(1_000)
		);

		assertTrue(HapticPatternSelection.availableSelections(
			CustomPatternLibrary.defaults()
		).contains(HapticPatternSelection.DESCENDING));
		assertEquals(0.8, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertTrue(pattern.getSteps().get(0).getIntensity()
			> pattern.getSteps().get(pattern.getSteps().size() - 1).getIntensity());
	}
}
