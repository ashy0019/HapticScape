package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;
import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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

	public SettingsLockService(Gson gson, HapticScapeStoragePaths storagePaths)
	{
		this(
			gson,
			Objects.requireNonNull(storagePaths, "storagePaths").getSettingsLockPath()
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
	 * lock. Local click output, pattern creation, and music controls deliberately
	 * remain local exceptions; an active remote session still applies its own
	 * authority rules.
	 */
	public boolean canEditLocally(String configKey)
	{
		return !getSnapshot().isLegacyFullLock()
			|| isLocalClickOutputKey(configKey)
			|| HapticScapeSettingKeys.CUSTOM_PATTERNS.equals(configKey)
			|| HapticScapeSettingKeys.MUSIC_SYNC_ENABLED.equals(configKey)
			|| HapticScapeSettingKeys.MUSIC_RESPONSE.equals(configKey)
			|| HapticScapeSettingKeys.MUSIC_SENSITIVITY_PERCENT.equals(configKey)
			|| HapticScapeSettingKeys.MUSIC_MINIMUM_INTENSITY_PERCENT.equals(configKey)
			|| HapticScapeSettingKeys.MUSIC_MAXIMUM_INTENSITY_PERCENT.equals(configKey);
	}

	public boolean canEditLocally(SettingsLockTarget target, String configKey)
	{
		if (isLocalClickOutputKey(configKey))
		{
			return true;
		}
		return canEditLocally(configKey)
			&& (target == null || !isLocked(target));
	}

	private static boolean isLocalClickOutputKey(String configKey)
	{
		return HapticScapeSettingKeys.CLICKER_ENABLED.equals(configKey)
			|| HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT.equals(configKey);
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

	public synchronized Optional<SettingsLockProposal> getProfileForOwner(String ownerId)
	{
		String requiredOwner = Objects.requireNonNull(ownerId, "ownerId");
		for (SettingsLockProposal lock : locks)
		{
			if (lock.isNamedProfile() && requiredOwner.equals(lock.getOwnerId()))
			{
				return Optional.of(lock);
			}
		}
		return Optional.empty();
	}

	/** Returns the controller owning the named profile which covers a target. */
	public synchronized Optional<String> getOwnerForTarget(SettingsLockTarget target)
	{
		SettingsLockTarget required = Objects.requireNonNull(target, "target");
		for (SettingsLockProposal lock : locks)
		{
			if (lock.isNamedProfile()
				&& SettingsLockCatalog.isCoveredBy(lock.getTargets(), required))
			{
				return Optional.of(lock.getOwnerId());
			}
		}
		return Optional.empty();
	}

	/**
	 * Atomically replaces the persistent profile owned by one controller.
	 * Unowned legacy bundles are preserved unless they overlap the accepted
	 * profile, in which case the explicit replacement migrates them away.
	 */
	public synchronized SettingsLockProposal replaceProfile(
		String ownerId,
		SettingsLockProposal proposal,
		String profileName)
	{
		SettingsLockProposal validated = Objects.requireNonNull(proposal, "proposal");
		validated.validate();
		if (validated.isLegacyFullLock())
		{
			throw new IllegalArgumentException("Named profiles require explicit lock targets");
		}
		validateCanReplace(ownerId, validated);
		SettingsLockProposal finalized = validated.withProfileMetadata(ownerId, profileName);
		finalized.validate();

		List<SettingsLockProposal> updated = new ArrayList<>();
		for (SettingsLockProposal current : locks)
		{
			if (current.isNamedProfile())
			{
				if (ownerId.equals(current.getOwnerId()))
				{
					continue;
				}
				updated.add(current);
				continue;
			}
			if (current.isLegacyFullLock()
				|| firstConflict(finalized.getTargets(), current.getTargets()) != null)
			{
				// Explicitly accepted replacement migrates an overlapping legacy bundle.
				continue;
			}
			updated.add(current);
		}
		updated.add(finalized);
		store.save(updated);
		locks = Collections.unmodifiableList(updated);
		publish();
		return finalized;
	}

	void validateCanReplace(String ownerId, SettingsLockProposal proposal)
	{
		String requiredOwner = Objects.requireNonNull(ownerId, "ownerId");
		SettingsLockProposal validated = Objects.requireNonNull(proposal, "proposal");
		validated.validate();
		if (validated.isLegacyFullLock())
		{
			validateCanArm(validated);
			return;
		}
		for (SettingsLockProposal current : locks)
		{
			if (!current.isNamedProfile() || requiredOwner.equals(current.getOwnerId()))
			{
				continue;
			}
			SettingsLockTarget overlap = firstConflict(
				validated.getTargets(),
				current.getTargets()
			);
			if (overlap != null)
			{
				throw new IllegalStateException(
					overlap.getDisplayName() + " is already locked by another controller profile"
				);
			}
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
		SettingsLockTarget overlap = firstConflict(
			validated.getTargets(),
			current.getTargets()
		);
		if (current.isLegacyFullLock()
			|| (validated.isLegacyFullLock() && current.isLocked())
			|| overlap != null)
		{
			String conflict = overlap == null
				? "Settings are already locked"
				: overlap.getDisplayName() + " overlaps an existing lock";
			throw new IllegalStateException(conflict);
		}
	}

	private static SettingsLockTarget firstConflict(
		Collection<SettingsLockTarget> proposed,
		Collection<SettingsLockTarget> existing)
	{
		for (SettingsLockTarget candidate : proposed)
		{
			for (SettingsLockTarget locked : existing)
			{
				if (SettingsLockCatalog.conflicts(candidate, locked))
				{
					return candidate;
				}
			}
		}
		return null;
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
		return unlock(null, password);
	}

	/** Removes the lock profile covering a required target when its password matches. */
	public synchronized boolean unlock(
		SettingsLockTarget requiredTarget,
		char[] password)
	{
		if (locks.isEmpty())
		{
			return requiredTarget == null;
		}
		char[] copy = Arrays.copyOf(
			Objects.requireNonNull(password, "password"),
			password.length
		);
		try
		{
			int matchingIndex = matchingLockIndex(copy, requiredTarget);
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

	/** Verifies a lock password for one protected action without removing the lock. */
	public synchronized boolean authorizes(
		SettingsLockTarget target,
		char[] password)
	{
		SettingsLockTarget requiredTarget = Objects.requireNonNull(target, "target");
		char[] copy = Arrays.copyOf(
			Objects.requireNonNull(password, "password"),
			password.length
		);
		try
		{
			return matchingLockIndex(copy, requiredTarget) >= 0;
		}
		finally
		{
			Arrays.fill(copy, '\0');
		}
	}

	private int matchingLockIndex(char[] password, SettingsLockTarget requiredTarget)
	{
		int exact = matchingLockIndexForCandidate(password, requiredTarget);
		if (exact >= 0)
		{
			return exact;
		}
		char[] normalized = normalizeGeneratedUnlockKey(password);
		if (normalized == null)
		{
			return -1;
		}
		try
		{
			return matchingLockIndexForCandidate(normalized, requiredTarget);
		}
		finally
		{
			Arrays.fill(normalized, '\0');
		}
	}

	private int matchingLockIndexForCandidate(
		char[] candidate,
		SettingsLockTarget requiredTarget)
	{
		for (int index = 0; index < locks.size(); index++)
		{
			SettingsLockProposal lock = locks.get(index);
			if ((requiredTarget == null
				|| SettingsLockCatalog.isCoveredBy(lock.getTargets(), requiredTarget))
				&& lock.verifies(candidate))
			{
				return index;
			}
		}
		return -1;
	}

	/** Accepts generated recovery keys despite case, spaces, or missing dashes. */
	private static char[] normalizeGeneratedUnlockKey(char[] value)
	{
		char[] compact = new char[UNLOCK_KEY_GROUPS * UNLOCK_KEY_GROUP_SIZE];
		int count = 0;
		for (char character : value)
		{
			if (character == '-' || Character.isWhitespace(character))
			{
				continue;
			}
			if (count == compact.length)
			{
				Arrays.fill(compact, '\0');
				return null;
			}
			char upper = Character.toUpperCase(character);
			if (!containsUnlockKeyCharacter(upper))
			{
				Arrays.fill(compact, '\0');
				return null;
			}
			compact[count++] = upper;
		}
		if (count != compact.length)
		{
			Arrays.fill(compact, '\0');
			return null;
		}
		char[] normalized = new char[compact.length + UNLOCK_KEY_GROUPS - 1];
		int source = 0;
		int target = 0;
		for (int group = 0; group < UNLOCK_KEY_GROUPS; group++)
		{
			if (group > 0)
			{
				normalized[target++] = '-';
			}
			for (int index = 0; index < UNLOCK_KEY_GROUP_SIZE; index++)
			{
				normalized[target++] = compact[source++];
			}
		}
		Arrays.fill(compact, '\0');
		return normalized;
	}

	private static boolean containsUnlockKeyCharacter(char candidate)
	{
		for (char allowed : UNLOCK_KEY_ALPHABET)
		{
			if (allowed == candidate)
			{
				return true;
			}
		}
		return false;
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
