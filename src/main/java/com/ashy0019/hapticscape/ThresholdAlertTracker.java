package com.ashy0019.hapticscape;

import java.util.EnumMap;

public final class ThresholdAlertTracker
{
	private final EnumMap<AlertCategory, Integer> previousValues =
		new EnumMap<>(AlertCategory.class);

	public void seed(AlertCategory category, int currentValue)
	{
		requireThresholdCategory(category);
		previousValues.put(category, currentValue);
	}

	public boolean update(AlertCategory category, int currentValue, int threshold)
	{
		requireThresholdCategory(category);
		Integer previous = previousValues.put(category, currentValue);
		return previous != null && category.getTriggerParameter().crossed(
			previous,
			currentValue,
			threshold
		);
	}

	public void reset()
	{
		previousValues.clear();
	}

	private static void requireThresholdCategory(AlertCategory category)
	{
		if (category == null
			|| !category.hasTriggerParameter()
			|| !category.getTriggerParameter().usesCrossingDetection())
		{
			throw new IllegalArgumentException("Expected a threshold-based alert category");
		}
	}
}
