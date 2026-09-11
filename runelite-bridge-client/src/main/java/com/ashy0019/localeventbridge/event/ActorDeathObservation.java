package com.ashy0019.localeventbridge.event;

import java.util.Objects;

/** Source-neutral observation that the local player died. */
public final class ActorDeathObservation implements LocalEvent
{
	public static final String TYPE = "actor.death";

	private final String source;

	public ActorDeathObservation(String source)
	{
		String normalized = Objects.requireNonNull(source, "source").trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException("source must not be empty");
		}
		this.source = normalized;
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
}
