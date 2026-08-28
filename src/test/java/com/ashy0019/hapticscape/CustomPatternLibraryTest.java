package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CustomPatternLibraryTest
{
	@Test
	public void namesAndCurvesRoundTripTogether()
	{
		CustomPatternLibrary original = CustomPatternLibrary.defaults()
			.withName(CustomPatternSlot.IV, "Dragon breath")
			.withPattern(CustomPatternSlot.IV, new CustomPattern(0, 25, 100, 0));

		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			original.toConfigValue()
		);

		assertEquals("Dragon breath", restored.getName(CustomPatternSlot.IV));
		assertEquals(original.get(CustomPatternSlot.IV), restored.get(CustomPatternSlot.IV));
	}

	@Test
	public void oldPrototypeStorageFallsBackToNewDefaults()
	{
		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			"v1|A=0,25,50,75,100,0,0,0"
		);

		assertEquals("Custom 1", restored.getName(CustomPatternSlot.I));
		assertTrue(restored.get(CustomPatternSlot.I).isSilent());
	}

	@Test
	public void previousDefaultNamesMigrateToCustomNames()
	{
		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			"v2|I=Rune I,0,100,0"
		);

		assertEquals("Custom 1", restored.getName(CustomPatternSlot.I));
	}

	@Test
	public void reservedNameCharactersAreSanitized()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withName(CustomPatternSlot.I, "Wave; = one, two");

		assertEquals("Wave one two", library.getName(CustomPatternSlot.I));
	}
}
