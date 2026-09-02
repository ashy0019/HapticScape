package com.ashy0019.hapticscape;

public enum AlertTriggerParameter
{
	HITPOINTS("At or below", 1, 200, 1, 20, Direction.DOWNWARD),
	PRAYER("At or below", 1, 200, 1, 10, Direction.DOWNWARD),
	LOOT_VALUE("Minimum GP", 1, 2_000_000_000, 1_000, 100_000, Direction.NONE),
	SPECIAL_ENERGY("Ready at (%)", 10, 100, 5, 100, Direction.UPWARD);

	private enum Direction
	{
		NONE,
		DOWNWARD,
		UPWARD
	}

	private final String label;
	private final int minimum;
	private final int maximum;
	private final int step;
	private final int defaultValue;
	private final Direction direction;

	AlertTriggerParameter(
		String label,
		int minimum,
		int maximum,
		int step,
		int defaultValue,
		Direction direction)
	{
		this.label = label;
		this.minimum = minimum;
		this.maximum = maximum;
		this.step = step;
		this.defaultValue = defaultValue;
		this.direction = direction;
	}

	public String getLabel()
	{
		return label;
	}

	public int getMinimum()
	{
		return minimum;
	}

	public int getMaximum()
	{
		return maximum;
	}

	public int getStep()
	{
		return step;
	}

	public int getDefaultValue()
	{
		return defaultValue;
	}

	public boolean usesCrossingDetection()
	{
		return direction != Direction.NONE;
	}

	public boolean crossed(int previousValue, int currentValue, int configuredValue)
	{
		switch (direction)
		{
			case DOWNWARD:
				return previousValue > configuredValue && currentValue <= configuredValue;
			case UPWARD:
				return previousValue < configuredValue && currentValue >= configuredValue;
			case NONE:
			default:
				throw new IllegalStateException(name() + " is not a crossing trigger");
		}
	}

	public int clamp(int value)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
