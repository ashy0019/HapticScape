package com.ashy0019.hapticscape.clicker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickSequenceTest
{
	@Test
	public void clickCountsAreExplicitAndBounded()
	{
		assertEquals(0, ClickSequence.NONE.getClickCount());
		assertEquals(1, ClickSequence.ONE.getClickCount());
		assertEquals(2, ClickSequence.TWO.getClickCount());
		assertEquals(3, ClickSequence.THREE.getClickCount());
		assertFalse(ClickSequence.NONE.isEnabled());
		assertTrue(ClickSequence.THREE.isEnabled());
	}

	@Test
	public void strongestKeepsOneBoundedSequenceInsteadOfStackingThem()
	{
		assertEquals(
			ClickSequence.THREE,
			ClickSequence.strongest(ClickSequence.ONE, ClickSequence.THREE)
		);
		assertEquals(
			ClickSequence.TWO,
			ClickSequence.strongest(ClickSequence.TWO, ClickSequence.ONE)
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void arbitraryClickCountsAreRejected()
	{
		ClickSequence.fromClickCount(4);
	}
}
