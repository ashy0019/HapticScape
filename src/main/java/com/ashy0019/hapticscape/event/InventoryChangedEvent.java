package com.ashy0019.hapticscape.event;

import java.util.Objects;

/**
 * Source-neutral observation of inventory occupancy. Integrations report slot
 * state; HapticScape decides whether becoming full should produce feedback.
 */
public final class InventoryChangedEvent implements HapticScapeEvent
{
	public static final String TYPE = "inventory_changed";

	private final String source;
	private final int filledSlots;
	private final int capacity;

	public InventoryChangedEvent(String source, int filledSlots, int capacity)
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
