package com.ashy0019.hapticscape.remote;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable local view of every persistent post-session lock. */
public final class SettingsLockSnapshot
{
	private final Set<String> lockIds;
	private final Set<SettingsLockTarget> targets;
	private final boolean legacyFullLock;

	SettingsLockSnapshot(
		Set<String> lockIds,
		Set<SettingsLockTarget> targets,
		boolean legacyFullLock)
	{
		this.lockIds = Collections.unmodifiableSet(new LinkedHashSet<>(lockIds));
		this.targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
		this.legacyFullLock = legacyFullLock;
	}

	public boolean isLocked()
	{
		return legacyFullLock || !targets.isEmpty();
	}

	public boolean isLegacyFullLock()
	{
		return legacyFullLock;
	}

	public int getLockCount()
	{
		return lockIds.size();
	}

	public Set<SettingsLockTarget> getTargets()
	{
		return targets;
	}

	public boolean isLocked(SettingsLockTarget target)
	{
		return legacyFullLock || targets.contains(target);
	}

	public boolean containsLock(String lockId)
	{
		return lockIds.contains(lockId);
	}
}
