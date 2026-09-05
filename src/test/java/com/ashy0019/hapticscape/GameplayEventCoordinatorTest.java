package com.ashy0019.hapticscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameplayEventCoordinatorTest
{
	@Test
	public void classifiesHealthyPoisonedAndVenomedStates()
	{
		assertEquals(0, GameplayEventCoordinator.classifyPoisonState(-1));
		assertEquals(0, GameplayEventCoordinator.classifyPoisonState(0));
		assertEquals(1, GameplayEventCoordinator.classifyPoisonState(1));
		assertEquals(1, GameplayEventCoordinator.classifyPoisonState(999_999));
		assertEquals(2, GameplayEventCoordinator.classifyPoisonState(1_000_000));
	}
}
