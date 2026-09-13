package com.ashy0019.hapticscape.remote;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RemoteLockSnapshot
{
	private final RemoteLockState state;
	private final String message;
	private final Set<SettingsLockTarget> targets;
	private final String profileId;
	private final String profileName;

	RemoteLockSnapshot(
		RemoteLockState state,
		String message,
		Collection<SettingsLockTarget> targets)
	{
		this(state, message, targets, null, null);
	}

	RemoteLockSnapshot(
		RemoteLockState state,
		String message,
		Collection<SettingsLockTarget> targets,
		String profileId,
		String profileName)
	{
		this.state = Objects.requireNonNull(state, "state");
		this.message = message == null ? "" : message;
		this.targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
		this.profileId = profileId;
		this.profileName = profileName;
	}

	public static RemoteLockSnapshot inactive()
	{
		return new RemoteLockSnapshot(
			RemoteLockState.INACTIVE,
			"No post-session lock requested",
			Collections.emptySet()
		);
	}

	public RemoteLockState getState()
	{
		return state;
	}

	public String getMessage()
	{
		return message;
	}

	public Set<SettingsLockTarget> getTargets()
	{
		return targets;
	}

	public String getProfileId()
	{
		return profileId;
	}

	public String getProfileName()
	{
		return profileName == null ? "" : profileName;
	}

	public boolean hasProfile()
	{
		return profileId != null && !getProfileName().isEmpty();
	}

	public boolean targets(SettingsLockTarget target)
	{
		return SettingsLockCatalog.isCoveredBy(targets, target);
	}
}
