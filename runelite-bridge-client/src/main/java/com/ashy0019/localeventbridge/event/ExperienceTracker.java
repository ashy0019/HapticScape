package com.ashy0019.localeventbridge.event;

import com.ashy0019.localeventbridge.EventIdentifiers;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Source-neutral XP state tracker. It knows only source/skill identifiers and
 * observed XP/level values; game-specific XP curves stay in source adapters.
 */
public final class ExperienceTracker
{
	private final Map<Key, State> previous = new HashMap<>();

	public ExperienceChangedEvent update(
		String source,
		String skillId,
		int currentXp,
		int currentLevel)
	{
		Key key = new Key(source, skillId);
		State prior = previous.put(key, new State(currentXp, currentLevel));

		if (prior == null)
		{
			return new ExperienceChangedEvent(
				key.source,
				key.skillId,
				currentXp,
				currentXp,
				0,
				currentLevel,
				currentLevel
			);
		}

		return new ExperienceChangedEvent(
			key.source,
			key.skillId,
			prior.xp,
			currentXp,
			Math.max(0, currentXp - prior.xp),
			prior.level,
			currentLevel
		);
	}

	public void seed(
		String source,
		String skillId,
		int currentXp,
		int currentLevel)
	{
		previous.put(
			new Key(source, skillId),
			new State(currentXp, currentLevel)
		);
	}

	public void reset()
	{
		previous.clear();
	}

	private static String normalizeIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}

	private static final class Key
	{
		private final String source;
		private final String skillId;

		private Key(String source, String skillId)
		{
			this.source = normalizeIdentifier(source, "source");
			this.skillId = EventIdentifiers.canonical(skillId);
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof Key))
			{
				return false;
			}
			Key key = (Key) other;
			return source.equals(key.source) && skillId.equals(key.skillId);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(source, skillId);
		}
	}

	private static final class State
	{
		private final int xp;
		private final int level;

		private State(int xp, int level)
		{
			this.xp = xp;
			this.level = level;
		}
	}
}
