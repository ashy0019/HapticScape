package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import java.util.Objects;

/**
 * One user-created haptic pattern, including the duration of one drawn beat
 * and the number of times it repeats. The numeric id is stable so renaming or
 * reordering an entry never breaks the places where it is assigned.
 */
public final class CustomPatternEntry
{
	public static final int MINIMUM_BEAT_DURATION_MILLIS = 50;
	public static final int MAXIMUM_BEAT_DURATION_MILLIS = 10_000;
	public static final int DEFAULT_BEAT_DURATION_MILLIS = 500;
	public static final int MINIMUM_BEAT_COUNT = 1;
	public static final int MAXIMUM_BEAT_COUNT = 72;
	public static final int DEFAULT_BEAT_COUNT = 1;

	private final int id;
	private final String name;
	private final CustomPattern pattern;
	private final int beatDurationMillis;
	private final int beatCount;

	CustomPatternEntry(
		int id,
		String name,
		CustomPattern pattern,
		int beatDurationMillis,
		int beatCount)
	{
		if (beatDurationMillis < MINIMUM_BEAT_DURATION_MILLIS
			|| beatDurationMillis > MAXIMUM_BEAT_DURATION_MILLIS)
		{
			throw new IllegalArgumentException("Custom beat duration is out of range");
		}
		if (beatCount < MINIMUM_BEAT_COUNT || beatCount > MAXIMUM_BEAT_COUNT)
		{
			throw new IllegalArgumentException("Custom beat count is out of range");
		}

		this.id = id;
		this.name = Objects.requireNonNull(name, "name");
		this.pattern = Objects.requireNonNull(pattern, "pattern");
		this.beatDurationMillis = beatDurationMillis;
		this.beatCount = beatCount;
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

	public int getBeatDurationMillis()
	{
		return beatDurationMillis;
	}

	public int getBeatCount()
	{
		return beatCount;
	}

	public int getTotalDurationMillis()
	{
		return beatDurationMillis * beatCount;
	}

	public HapticPattern createPattern(double maximumIntensity)
	{
		return pattern.createPattern(
			maximumIntensity,
			Duration.ofMillis(beatDurationMillis)
		).repeated(beatCount);
	}

	CustomPatternEntry withName(String updatedName)
	{
		return new CustomPatternEntry(
			id,
			updatedName,
			pattern,
			beatDurationMillis,
			beatCount
		);
	}

	CustomPatternEntry withPattern(CustomPattern updatedPattern)
	{
		return new CustomPatternEntry(
			id,
			name,
			updatedPattern,
			beatDurationMillis,
			beatCount
		);
	}

	CustomPatternEntry withPlayback(int updatedBeatDurationMillis, int updatedBeatCount)
	{
		return new CustomPatternEntry(
			id,
			name,
			pattern,
			updatedBeatDurationMillis,
			updatedBeatCount
		);
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
