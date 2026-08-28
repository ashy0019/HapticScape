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
	public void customSelectionsResolveCurrentSavedCurve()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withPattern(CustomPatternSlot.II, new CustomPattern(100, 0));
		HapticPattern pattern = HapticPatternSelection.CUSTOM_II.createPattern(
			library,
			0.5,
			Duration.ofMillis(100)
		);

		assertEquals(0.5, pattern.getSteps().get(0).getIntensity(), 0.0001);
		assertEquals(0.0, pattern.getSteps().get(1).getIntensity(), 0.0001);
	}

	@Test
	public void renamedSlotsAppearInPatternSelectors()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withName(CustomPatternSlot.III, "Goblin drum");

		assertEquals(
			"Goblin drum",
			HapticPatternSelection.CUSTOM_III.getDisplayName(library)
		);
	}
}
