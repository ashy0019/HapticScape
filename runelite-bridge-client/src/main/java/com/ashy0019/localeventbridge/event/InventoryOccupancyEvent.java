package com.ashy0019.localeventbridge.event;

import java.util.Objects;

/**
 * Source-neutral observation of inventory occupancy. Integrations report slot
 * state without exposing RuneLite container objects.
 */
public final class InventoryOccupancyEvent implements LocalEvent
{
	public static final String TYPE = "inventory.occupancy";

	private final String source;
	private final int filledSlots;
	private final int capacity;

	public InventoryOccupancyEvent(String source, int filledSlots, int capacity)
	{
		this.source = requireIdentifier(source, "source");
		if (filledSlots < 0)
		{
			throw new IllegalArgumentException("filledSlots must not be negative");
		}
		if (capacity < 0)
		{
			throw new IllegalArgumentException("capacity must not be negative");
		}
		this.filledSlots = Math.min(filledSlots, capacity);
		this.capacity = capacity;
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

	public int getFilledSlots()
	{
		return filledSlots;
	}

	public int getCapacity()
	{
		return capacity;
	}

	public boolean isFull()
	{
		return capacity > 0 && filledSlots >= capacity;
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
