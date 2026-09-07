package com.ashy0019.hapticscape;

import java.util.Objects;

/** Source-neutral skill metadata used by settings and UI presentation. */
public final class SkillDescriptor
{
	private final String id;
	private final String displayName;

	public SkillDescriptor(String id, String displayName)
	{
		this.id = SkillIds.canonical(id);
		this.displayName = Objects.requireNonNull(displayName, "displayName").trim();
		if (this.displayName.isEmpty())
		{
			throw new IllegalArgumentException("displayName must not be empty");
		}
	}

	public String getId()
	{
		return id;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
