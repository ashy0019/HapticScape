package com.ashy0019.hapticscape.clicker;

import java.util.Objects;

/**
 * Deliberately small click-feedback vocabulary.
 *
 * <p>Click feedback is constrained to silence, one click, two clicks, or
 * three clicks. Timing is owned by {@link ClickerService}; callers choose
 * semantic weight, not an arbitrary rhythm.</p>
 */
public enum ClickSequence
{
	NONE(0),
	ONE(1),
	TWO(2),
	THREE(3);

	private final int clickCount;

	ClickSequence(int clickCount)
	{
		this.clickCount = clickCount;
	}

	public int getClickCount()
	{
		return clickCount;
	}

	public boolean isEnabled()
	{
		return this != NONE;
	}

	public static ClickSequence fromClickCount(int clickCount)
	{
		switch (clickCount)
		{
			case 0:
				return NONE;
			case 1:
				return ONE;
			case 2:
				return TWO;
			case 3:
				return THREE;
			default:
				throw new IllegalArgumentException("Click count must be between 0 and 3");
		}
	}

	public static ClickSequence strongest(ClickSequence first, ClickSequence second)
	{
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(second, "second");
		return first.clickCount >= second.clickCount ? first : second;
	}
}
