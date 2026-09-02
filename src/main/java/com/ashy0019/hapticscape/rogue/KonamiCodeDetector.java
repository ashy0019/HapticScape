package com.ashy0019.hapticscape.rogue;

import java.awt.event.KeyEvent;

/**
 * Small state machine for the classic Konami sequence.
 */
public final class KonamiCodeDetector
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

	private int position;

	/**
	 * Observes one key press.
	 *
	 * @return true exactly when the complete sequence has just been entered
	 */
	public boolean acceptKeyCode(int keyCode)
	{
		if (keyCode == CODE[position])
		{
			position++;
			if (position == CODE.length)
			{
				position = 0;
				return true;
			}
			return false;
		}

		// The code begins with two UP presses. Treat a mismatching UP as a new
		// possible start instead of throwing it away completely.
		position = keyCode == CODE[0] ? 1 : 0;
		return false;
	}

	public void reset()
	{
		position = 0;
	}

	int getPosition()
	{
		return position;
	}
}
