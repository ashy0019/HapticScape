package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.client.RuneLite;

/** Owns the persistent local settings lock. Safety controls do not consult it. */
public final class SettingsLockService
{
	private static final char[] UNLOCK_KEY_ALPHABET =
		"ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int UNLOCK_KEY_GROUPS = 5;
	private static final int UNLOCK_KEY_GROUP_SIZE = 4;

	private final SettingsLockStore store;
	private final SecureRandom random = new SecureRandom();
	private final CopyOnWriteArrayList<SettingsLockListener> listeners =
		new CopyOnWriteArrayList<>();
	private volatile List<SettingsLockProposal> locks;

	public SettingsLockService(Gson gson)
	{
		this(
			gson,
			RuneLite.RUNELITE_DIR.toPath()
				.resolve("hapticscape")
				.resolve("settings-lock.json")
		);
	}

	SettingsLockService(Gson gson, Path path)
	{
		store = new SettingsLockStore(
			Objects.requireNonNull(gson, "gson"),
			Objects.requireNonNull(path, "path")
		);
		locks = Collections.unmodifiableList(new ArrayList<>(store.load()));
	}

	public boolean isLocked()
	{
		return !locks.isEmpty();
	}

	public SettingsLockSnapshot getSnapshot()
	{
		Set<String> lockIds = new HashSet<>();
		Set<SettingsLockTarget> targets = new HashSet<>();
		boolean legacy = false;
		for (SettingsLockProposal lock : locks)
		{
			lockIds.add(lock.getProposalId());
			targets.addAll(lock.getTargets());
			legacy |= lock.isLegacyFullLock();
		}
		return new SettingsLockSnapshot(lockIds, targets, legacy);
	}

	public boolean isLocked(SettingsLockTarget target)
	{
		return getSnapshot().isLocked(Objects.requireNonNull(target, "target"));
	}

	/**
	 * Returns whether a local configuration write is allowed by the persistent
	 * lock. Pattern creation and music controls deliberately remain local safety
	 * exceptions; an active remote session still applies its own authority rules.
	 */
	public boolean canEditLocally(String configKey)
	{
		return !getSnapshot().isLegacyFullLock()
			|| HapticScapeConfig.CUSTOM_PATTERNS_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_SYNC_ENABLED_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_RESPONSE_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_SENSITIVITY_PERCENT_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_MINIMUM_INTENSITY_PERCENT_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY.equals(configKey);
	}

	public boolean canEditLocally(SettingsLockTarget target, String configKey)
	{
		return canEditLocally(configKey)
			&& (target == null || !isLocked(target));
	}

	/**
	 * Generates a readable 100-bit unlock key without ambiguous characters.
	 * The caller owns the returned array and must erase it after use.
	 */
	public char[] generateUnlockKey()
	{
		int separatorCount = UNLOCK_KEY_GROUPS - 1;
		char[] key = new char[
			UNLOCK_KEY_GROUPS * UNLOCK_KEY_GROUP_SIZE + separatorCount
		];
		int index = 0;
		for (int group = 0; group < UNLOCK_KEY_GROUPS; group++)
		{
			if (group > 0)
			{
				key[index++] = '-';
			}
			for (int character = 0; character < UNLOCK_KEY_GROUP_SIZE; character++)
			{
				key[index++] = UNLOCK_KEY_ALPHABET[random.nextInt(UNLOCK_KEY_ALPHABET.length)];
			}
		}
		return key;
	}

	public SettingsLockProposal createProposal(char[] password)
	{
		char[] copy = Arrays.copyOf(
			Objects.requireNonNull(password, "password"),
			password.length
		);
		try
		{
			return SettingsLockProposal.create(copy);
		}
		finally
		{
			Arrays.fill(copy, '\0');
		}
	}

	public SettingsLockProposal createProposal(
		char[] password,
		Collection<SettingsLockTarget> targets)
	{
		char[] copy = Arrays.copyOf(
			Objects.requireNonNull(password, "password"),
			password.length
		);
		try
		{
			return SettingsLockProposal.create(copy, targets);
		}
		finally
		{
			Arrays.fill(copy, '\0');
		}
	}

	public synchronized void arm(SettingsLockProposal proposal)
	{
		SettingsLockProposal validated = Objects.requireNonNull(proposal, "proposal");
		validated.validate();
		validateCanArm(validated);
		List<SettingsLockProposal> updated = new ArrayList<>(locks);
		updated.add(validated);
		store.save(updated);
		locks = Collections.unmodifiableList(updated);
		publish();
	}

	void validateCanArm(SettingsLockProposal proposal)
	{
		SettingsLockProposal validated = Objects.requireNonNull(proposal, "proposal");
		SettingsLockSnapshot current = getSnapshot();
		Set<SettingsLockTarget> overlap = new HashSet<>(validated.getTargets());
		overlap.retainAll(current.getTargets());
		if (current.isLegacyFullLock() || !overlap.isEmpty())
		{
			String conflict = overlap.isEmpty()
				? "Settings are already locked"
				: overlap.iterator().next().getDisplayName() + " is already locked";
			throw new IllegalStateException(conflict);
		}
	}

	public synchronized boolean removeLock(String lockId)
	{
		List<SettingsLockProposal> updated = new ArrayList<>(locks);
		boolean removed = updated.removeIf(lock -> lock.getProposalId().equals(lockId));
		if (!removed)
		{
			return false;
		}
		if (updated.isEmpty())
		{
			store.clear();
		}
		else
		{
			store.save(updated);
		}
		locks = Collections.unmodifiableList(updated);
		publish();
		return true;
	}

	public synchronized boolean unlock(char[] password)
	{
		if (locks.isEmpty())
		{
			return true;
		}
		char[] copy = Arrays.copyOf(
			Objects.requireNonNull(password, "password"),
			password.length
		);
		try
		{
			int matchingIndex = -1;
			for (int index = 0; index < locks.size(); index++)
			{
				if (locks.get(index).verifies(copy))
				{
					matchingIndex = index;
					break;
				}
			}
			if (matchingIndex < 0)
			{
				return false;
			}
			List<SettingsLockProposal> updated = new ArrayList<>(locks);
			updated.remove(matchingIndex);
			if (updated.isEmpty())
			{
				store.clear();
			}
			else
			{
				store.save(updated);
			}
			locks = Collections.unmodifiableList(updated);
			publish();
			return true;
		}
		finally
		{
			Arrays.fill(copy, '\0');
		}
	}

	public synchronized void clearAllLocks()
	{
		store.clear();
		boolean changed = !locks.isEmpty();
		locks = Collections.emptyList();
		if (changed)
		{
			publish();
		}
	}

	public void addListener(SettingsLockListener listener)
	{
		SettingsLockListener required = Objects.requireNonNull(listener, "listener");
		listeners.add(required);
		required.onSettingsLockChanged(getSnapshot());
	}

	public void removeListener(SettingsLockListener listener)
	{
		listeners.remove(listener);
	}

	private void publish()
	{
		SettingsLockSnapshot snapshot = getSnapshot();
		for (SettingsLockListener listener : listeners)
		{
			listener.onSettingsLockChanged(snapshot);
		}
	}
}
