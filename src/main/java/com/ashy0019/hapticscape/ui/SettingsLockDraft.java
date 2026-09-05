package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockTarget;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Controller-local selection of settings for the next lock proposal. */
final class SettingsLockDraft
{
	private final Set<SettingsLockTarget> targets = new LinkedHashSet<>();
	private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

	synchronized void toggle(SettingsLockTarget target)
	{
		if (!targets.remove(target))
		{
			targets.removeIf(existing -> SettingsLockCatalog.conflicts(existing, target));
			targets.add(target);
		}
		publish();
	}

	synchronized void setAll(
		Set<SettingsLockTarget> changedTargets,
		boolean selected)
	{
		boolean changed = selected
			? addAllNormalized(changedTargets)
			: targets.removeAll(changedTargets);
		if (changed)
		{
			publish();
		}
	}

	private boolean addAllNormalized(Set<SettingsLockTarget> changedTargets)
	{
		boolean changed = false;
		for (SettingsLockTarget target : changedTargets)
		{
			Set<SettingsLockTarget> conflicts = new LinkedHashSet<>();
			for (SettingsLockTarget existing : targets)
			{
				if (SettingsLockCatalog.conflicts(existing, target))
				{
					conflicts.add(existing);
				}
			}
			changed |= targets.removeAll(conflicts);
			changed |= targets.add(target);
		}
		return changed;
	}

	synchronized boolean contains(SettingsLockTarget target)
	{
		return targets.contains(target);
	}

	synchronized Set<SettingsLockTarget> snapshot()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(targets));
	}

	synchronized int size()
	{
		return targets.size();
	}

	synchronized void clear()
	{
		if (!targets.isEmpty())
		{
			targets.clear();
			publish();
		}
	}

	void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	private void publish()
	{
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
