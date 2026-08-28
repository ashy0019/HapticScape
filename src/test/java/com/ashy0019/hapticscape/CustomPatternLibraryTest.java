package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomPatternLibraryTest
{
	@Test
	public void freshLibraryStartsWithOneBlankPattern()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults();

		assertEquals(1, library.size());
		assertEquals("Custom 1", entry(library, 1).getName());
		assertTrue(entry(library, 1).getPattern().isSilent());
	}

	@Test
	public void namesAndCurvesRoundTripTogether()
	{
		CustomPatternLibrary original = CustomPatternLibrary.defaults()
			.addBlankPattern()
			.withName(2, "Dragon breath")
			.withPattern(2, new CustomPattern(0, 25, 100, 0));

		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			original.toConfigValue()
		);

		assertEquals("Dragon breath", entry(restored, 2).getName());
		assertEquals(entry(original, 2).getPattern(), entry(restored, 2).getPattern());
	}

	@Test
	public void deletedIdsAreNotReusedAfterRoundTrip()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.addBlankPattern()
			.withoutPattern(2);
		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			library.toConfigValue()
		).addBlankPattern();

		assertFalse(restored.contains(2));
		assertTrue(restored.contains(3));
	}

	@Test
	public void defaultNamesRestartAfterHigherVisibleSlotsAreDeleted()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults();
		for (int index = 1; index < CustomPatternLibrary.MAXIMUM_PATTERN_COUNT; index++)
		{
			library = library.addBlankPattern();
		}
		for (int id = 2; id <= CustomPatternLibrary.MAXIMUM_PATTERN_COUNT; id++)
		{
			library = library.withoutPattern(id);
		}

		library = library.addBlankPattern();

		assertEquals(101, library.getPatterns().get(1).getId());
		assertEquals("Custom 2", library.getPatterns().get(1).getName());
	}

	@Test
	public void oldPrototypeStorageFallsBackToNewDefaults()
	{
		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			"v1|A=0,25,50,75,100,0,0,0"
		);

		assertEquals(1, restored.size());
		assertEquals("Custom 1", entry(restored, 1).getName());
		assertTrue(entry(restored, 1).getPattern().isSilent());
	}

	@Test
	public void fixedSlotStorageMigratesWithoutLosingPatterns()
	{
		CustomPatternLibrary restored = CustomPatternLibrary.fromConfigValue(
			"v2|I=Rune I,0,100,0;II=Goblin drum,100,50,0"
		);

		assertEquals(2, restored.size());
		assertEquals("Custom 1", entry(restored, 1).getName());
		assertEquals("Goblin drum", entry(restored, 2).getName());
		assertEquals(new CustomPattern(100, 50, 0), entry(restored, 2).getPattern());
	}

	@Test
	public void reservedNameCharactersAreSanitized()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults()
			.withName(1, "Wave; = one, two");

		assertEquals("Wave one two", entry(library, 1).getName());
	}

	@Test
	public void libraryStopsAtOneHundredPatterns()
	{
		CustomPatternLibrary library = CustomPatternLibrary.defaults();
		for (int index = 1; index < CustomPatternLibrary.MAXIMUM_PATTERN_COUNT; index++)
		{
			library = library.addBlankPattern();
		}

		assertEquals(100, library.size());
		assertFalse(library.canAddPattern());
		assertEquals(library, library.addBlankPattern());
	}

	private static CustomPatternEntry entry(CustomPatternLibrary library, int id)
	{
		return library.findById(id).orElseThrow(AssertionError::new);
	}
}
