package com.ashy0019.hapticscape.rogue;

import java.awt.event.KeyEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KonamiCodeDetectorTest
{
	private static final int[] CODE = {
		KeyEvent.VK_UP,
		KeyEvent.VK_UP,
		KeyEvent.VK_DOWN,
		KeyEvent.VK_DOWN,
		KeyEvent.VK_LEFT,
		KeyEvent.VK_RIGHT,
		KeyEvent.VK_LEFT,
		KeyEvent.VK_RIGHT,
		KeyEvent.VK_B,
		KeyEvent.VK_A
	};

	@Test
	public void exactSequenceUnlocks()
	{
		KonamiCodeDetector detector = new KonamiCodeDetector();
		for (int index = 0; index < CODE.length - 1; index++)
		{
			assertFalse(detector.acceptKeyCode(CODE[index]));
		}
		assertTrue(detector.acceptKeyCode(CODE[CODE.length - 1]));
	}

	@Test
	public void wrongKeyResetsSequence()
	{
		KonamiCodeDetector detector = new KonamiCodeDetector();
		assertFalse(detector.acceptKeyCode(KeyEvent.VK_UP));
		assertFalse(detector.acceptKeyCode(KeyEvent.VK_UP));
		assertFalse(detector.acceptKeyCode(KeyEvent.VK_X));
		for (int keyCode : CODE)
		{
			if (keyCode == CODE[CODE.length - 1])
			{
				assertTrue(detector.acceptKeyCode(keyCode));
			}
			else
			{
				assertFalse(detector.acceptKeyCode(keyCode));
			}
		}
	}

	@Test
	public void detectorCanUnlockTwice()
	{
		KonamiCodeDetector detector = new KonamiCodeDetector();
		assertTrue(play(detector));
		assertTrue(play(detector));
	}

	private static boolean play(KonamiCodeDetector detector)
	{
		boolean unlocked = false;
		for (int keyCode : CODE)
		{
			unlocked = detector.acceptKeyCode(keyCode);
		}
		return unlocked;
	}
}
