package com.ashy0019.hapticscape.remote;

import java.util.Objects;

/** Stable logical identifier for one independently lockable setting. */
public final class SettingsLockTarget implements Comparable<SettingsLockTarget>
{
	private final String id;
	private final String group;
	private final String displayName;

	SettingsLockTarget(String id, String group, String displayName)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.group = Objects.requireNonNull(group, "group");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
	}

	public String getId()
	{
		return id;
	}

	public String getGroup()
	{
		return group;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public int compareTo(SettingsLockTarget other)
	{
		int groupComparison = group.compareTo(other.group);
		return groupComparison != 0
			? groupComparison
			: displayName.compareTo(other.displayName);
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof SettingsLockTarget
			&& id.equals(((SettingsLockTarget) other).id);
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
