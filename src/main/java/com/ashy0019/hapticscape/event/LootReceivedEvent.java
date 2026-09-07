package com.ashy0019.hapticscape.event;

import java.util.Objects;

/**
 * Source-neutral observation of a batch of loot. Integrations resolve
 * game-specific market values; HapticScape decides whether that value is
 * important enough to produce feedback.
 */
public final class LootReceivedEvent implements HapticScapeEvent
{
	public static final String TYPE = "loot_received";

	private final String source;
	private final int stackCount;
	private final long totalValue;

	public LootReceivedEvent(String source, int stackCount, long totalValue)
	{
		this.source = requireIdentifier(source, "source");
		if (stackCount < 0)
		{
			throw new IllegalArgumentException("stackCount must not be negative");
		}
		if (totalValue < 0)
		{
			throw new IllegalArgumentException("totalValue must not be negative");
		}
		this.stackCount = stackCount;
		this.totalValue = totalValue;
	}

	@Override
	public String getSource()
	{
		return source;
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	public int getStackCount()
	{
		return stackCount;
	}

	public long getTotalValue()
	{
		return totalValue;
	}

	private static String requireIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}
}
