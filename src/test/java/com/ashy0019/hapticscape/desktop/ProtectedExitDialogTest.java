package com.ashy0019.hapticscape.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProtectedExitDialogTest
{
	@Test
	public void bypassUnlocksAfterTenSeconds()
	{
		assertEquals(10, ProtectedExitDialog.remainingSeconds(0));
		assertEquals(10, ProtectedExitDialog.remainingSeconds(999));
		assertEquals(1, ProtectedExitDialog.remainingSeconds(9_000));
		assertEquals(0, ProtectedExitDialog.remainingSeconds(10_000));
		assertEquals(0, ProtectedExitDialog.remainingSeconds(15_000));
	}
}
