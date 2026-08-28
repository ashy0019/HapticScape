package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
			.withPattern(2, new CustomPattern(100, 0));
		HapticPattern pattern = HapticPatternSelection.custom(2).createPattern(
			library,
			0.5,
			Duration.ofMillis(100)
		);

		assertEquals(0.5, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.0, pattern.getSteps().get(1).getIntensity(), 0.0001);
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
}
