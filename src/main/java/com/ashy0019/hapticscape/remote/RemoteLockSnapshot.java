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

	RemoteLockSnapshot(
		RemoteLockState state,
		String message,
		Collection<SettingsLockTarget> targets)
	{
		this.state = Objects.requireNonNull(state, "state");
		this.message = message == null ? "" : message;
		this.targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
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

	public boolean targets(SettingsLockTarget target)
	{
		return SettingsLockCatalog.isCoveredBy(targets, target);
	}
}
