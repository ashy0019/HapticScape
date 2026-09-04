package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
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
	private volatile SettingsLockProposal current;

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
		current = store.load().orElse(null);
	}

	public boolean isLocked()
	{
		return current != null;
	}

	/**
	 * Returns whether a local configuration write is allowed by the persistent
	 * lock. Pattern creation and music controls deliberately remain local safety
	 * exceptions; an active remote session still applies its own authority rules.
	 */
	public boolean canEditLocally(String configKey)
	{
		return !isLocked()
			|| HapticScapeConfig.CUSTOM_PATTERNS_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_SYNC_ENABLED_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_RESPONSE_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_SENSITIVITY_PERCENT_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_MINIMUM_INTENSITY_PERCENT_KEY.equals(configKey)
			|| HapticScapeConfig.MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY.equals(configKey);
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

	public synchronized void arm(SettingsLockProposal proposal)
	{
		SettingsLockProposal validated = Objects.requireNonNull(proposal, "proposal");
		validated.validate();
		if (current != null)
		{
			throw new IllegalStateException("Settings are already locked");
		}
		store.save(validated);
		current = validated;
		publish(true);
	}

	public synchronized boolean unlock(char[] password)
	{
		SettingsLockProposal lock = current;
		if (lock == null)
		{
			return true;
		}
		char[] copy = Arrays.copyOf(
			Objects.requireNonNull(password, "password"),
			password.length
		);
		try
		{
			if (!lock.verifies(copy))
			{
				return false;
			}
			clearAllLocks();
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
		boolean changed = current != null;
		current = null;
		if (changed)
		{
			publish(false);
		}
	}

	public void addListener(SettingsLockListener listener)
	{
		SettingsLockListener required = Objects.requireNonNull(listener, "listener");
		listeners.add(required);
		required.onSettingsLockChanged(isLocked());
	}

	public void removeListener(SettingsLockListener listener)
	{
		listeners.remove(listener);
	}

	private void publish(boolean locked)
	{
		for (SettingsLockListener listener : listeners)
		{
			listener.onSettingsLockChanged(locked);
		}
	}
}
