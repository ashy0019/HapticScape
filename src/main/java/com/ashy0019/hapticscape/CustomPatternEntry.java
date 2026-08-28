package com.ashy0019.hapticscape;

import java.util.Objects;

/**
 * One user-created haptic pattern. The numeric id is stable so renaming or
 * reordering an entry never breaks the places where it is assigned.
 */
public final class CustomPatternEntry
{
	private final int id;
	private final String name;
	private final CustomPattern pattern;

	CustomPatternEntry(int id, String name, CustomPattern pattern)
	{
		this.id = id;
		this.name = Objects.requireNonNull(name, "name");
		this.pattern = Objects.requireNonNull(pattern, "pattern");
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public CustomPattern getPattern()
	{
		return pattern;
	}

	CustomPatternEntry withName(String updatedName)
	{
		return new CustomPatternEntry(id, updatedName, pattern);
	}

	CustomPatternEntry withPattern(CustomPattern updatedPattern)
	{
		return new CustomPatternEntry(id, name, updatedPattern);
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof CustomPatternEntry
			&& id == ((CustomPatternEntry) other).id;
	}

	@Override
	public int hashCode()
	{
		return Integer.hashCode(id);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
