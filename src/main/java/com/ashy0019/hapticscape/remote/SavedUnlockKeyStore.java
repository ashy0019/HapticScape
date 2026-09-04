package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.runelite.client.RuneLite;

/** Persistent controller vault containing only DPAPI-protected unlock keys. */
public final class SavedUnlockKeyStore
{
	private static final Logger LOG = Logger.getLogger(SavedUnlockKeyStore.class.getName());
	private static final int SCHEMA_VERSION = 1;
	private static final int MAXIMUM_ENTRIES = 1_000;
	private static final long MAXIMUM_VAULT_BYTES = 5L * 1024L * 1024L;
	private static final DateTimeFormatter DEFAULT_LABEL_FORMAT = DateTimeFormatter.ofPattern(
		"'Session' MMM d, h:mm a",
		Locale.ENGLISH
	);

	private final Gson gson;
	private final Path path;
	private final UnlockKeyProtector protector;
	private final Clock clock;
	private List<SavedUnlockKey> entries = Collections.emptyList();
	private String loadFailure;

	public SavedUnlockKeyStore(Gson gson)
	{
		this(
			gson,
			RuneLite.RUNELITE_DIR.toPath()
				.resolve("hapticscape")
				.resolve("saved-unlock-keys.json"),
			new WindowsDpapiUnlockKeyProtector(),
			Clock.systemDefaultZone()
		);
	}

	SavedUnlockKeyStore(
		Gson gson,
		Path path,
		UnlockKeyProtector protector,
		Clock clock)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.path = Objects.requireNonNull(path, "path");
		this.protector = Objects.requireNonNull(protector, "protector");
		this.clock = Objects.requireNonNull(clock, "clock");
		if (protector.isAvailable())
		{
			load();
		}
	}

	static SavedUnlockKeyStore disabled(Gson gson)
	{
		return new SavedUnlockKeyStore(
			gson,
			java.nio.file.Paths.get("saved-unlock-keys-disabled.json"),
			new UnavailableProtector(),
			Clock.systemUTC()
		);
	}

	public boolean isAvailable()
	{
		return protector.isAvailable() && loadFailure == null;
	}

	public String getUnavailableMessage()
	{
		if (!protector.isAvailable())
		{
			return protector.getUnavailableMessage();
		}
		return loadFailure == null ? "" : loadFailure;
	}

	public synchronized List<SavedUnlockKey> list()
	{
		return Collections.unmodifiableList(new ArrayList<>(entries));
	}

	public synchronized Optional<SavedUnlockKey> findByLockId(String lockId)
	{
		return entries.stream()
			.filter(entry -> entry.getLockId().equals(lockId))
			.findFirst();
	}

	public synchronized SavedUnlockKey saveAcceptedKey(String lockId, char[] unlockKey)
	{
		ensureAvailable();
		Objects.requireNonNull(lockId, "lockId");
		Objects.requireNonNull(unlockKey, "unlockKey");
		Optional<SavedUnlockKey> existing = findByLockId(lockId);
		if (existing.isPresent())
		{
			return existing.get();
		}

		byte[] plaintext = toAscii(unlockKey);
		byte[] protectedBytes = null;
		try
		{
			protectedBytes = protector.protect(plaintext);
			Instant now = clock.instant();
			String label = DEFAULT_LABEL_FORMAT
				.withZone(clock.getZone())
				.format(now);
			SavedUnlockKey entry = new SavedUnlockKey(
				UUID.randomUUID().toString(),
				label,
				lockId,
				now.toEpochMilli(),
				0,
				"",
				Base64.getEncoder().encodeToString(protectedBytes)
			);
			entry.validate();
			List<SavedUnlockKey> updated = new ArrayList<>(entries);
			updated.add(0, entry);
			persist(updated);
			entries = Collections.unmodifiableList(updated);
			return entry;
		}
		finally
		{
			Arrays.fill(plaintext, (byte) 0);
			if (protectedBytes != null)
			{
				Arrays.fill(protectedBytes, (byte) 0);
			}
		}
	}

	public synchronized SavedUnlockKey updateDetails(
		String id,
		String label,
		String note)
	{
		ensureAvailable();
		int index = indexOf(id);
		SavedUnlockKey updatedEntry = entries.get(index).withDetails(label, note);
		List<SavedUnlockKey> updated = new ArrayList<>(entries);
		updated.set(index, updatedEntry);
		persist(updated);
		entries = Collections.unmodifiableList(updated);
		return updatedEntry;
	}

	/** Returns a caller-owned key array and records successful access. */
	public synchronized char[] reveal(String id)
	{
		ensureAvailable();
		int index = indexOf(id);
		SavedUnlockKey entry = entries.get(index);
		byte[] protectedBytes;
		try
		{
			protectedBytes = Base64.getDecoder().decode(entry.getProtectedKey());
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalStateException("The saved unlock key is damaged", e);
		}
		byte[] plaintext = null;
		char[] key = null;
		try
		{
			plaintext = protector.unprotect(protectedBytes);
			key = fromAscii(plaintext);
			SavedUnlockKey accessed = entry.withLastUsedAt(clock.millis());
			List<SavedUnlockKey> updated = new ArrayList<>(entries);
			updated.set(index, accessed);
			persist(updated);
			entries = Collections.unmodifiableList(updated);
			return key;
		}
		catch (RuntimeException e)
		{
			if (key != null)
			{
				Arrays.fill(key, '\0');
			}
			throw e;
		}
		finally
		{
			Arrays.fill(protectedBytes, (byte) 0);
			if (plaintext != null)
			{
				Arrays.fill(plaintext, (byte) 0);
			}
		}
	}

	public synchronized boolean forget(String id)
	{
		ensureAvailable();
		int index = findIndex(id);
		if (index < 0)
		{
			return false;
		}
		List<SavedUnlockKey> updated = new ArrayList<>(entries);
		updated.remove(index);
		persist(updated);
		entries = Collections.unmodifiableList(updated);
		return true;
	}

	synchronized boolean forgetByLockId(String lockId)
	{
		if (!isAvailable())
		{
			return false;
		}
		List<SavedUnlockKey> updated = new ArrayList<>(entries);
		boolean removed = updated.removeIf(entry -> entry.getLockId().equals(lockId));
		if (removed)
		{
			persist(updated);
			entries = Collections.unmodifiableList(updated);
		}
		return removed;
	}

	private void load()
	{
		if (!Files.isRegularFile(path))
		{
			return;
		}
		try
		{
			if (Files.size(path) > MAXIMUM_VAULT_BYTES)
			{
				throw new IllegalArgumentException("Saved-key vault is unexpectedly large");
			}
			String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			VaultFile vault = gson.fromJson(json, VaultFile.class);
			if (vault == null || vault.schemaVersion != SCHEMA_VERSION)
			{
				throw new IllegalArgumentException("Unsupported saved-key vault format");
			}
			List<SavedUnlockKey> loaded = vault.entries == null
				? Collections.emptyList()
				: new ArrayList<>(vault.entries);
			if (loaded.size() > MAXIMUM_ENTRIES)
			{
				throw new IllegalArgumentException("Saved-key vault contains too many entries");
			}
			Set<String> entryIds = new HashSet<>();
			Set<String> lockIds = new HashSet<>();
			for (SavedUnlockKey entry : loaded)
			{
				entry.validate();
				if (!entryIds.add(entry.getId()) || !lockIds.add(entry.getLockId()))
				{
					throw new IllegalArgumentException("Saved-key vault contains duplicates");
				}
			}
			entries = Collections.unmodifiableList(loaded);
		}
		catch (Exception e)
		{
			loadFailure = "Saved Unlock Keys could not read the existing vault";
			LOG.log(Level.WARNING, loadFailure, e);
		}
	}

	private void persist(List<SavedUnlockKey> updated)
	{
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(path.getParent());
			if (updated.isEmpty())
			{
				Files.deleteIfExists(path);
				Files.deleteIfExists(temporary);
				return;
			}
			Files.write(
				temporary,
				gson.toJson(new VaultFile(SCHEMA_VERSION, updated))
					.getBytes(StandardCharsets.UTF_8)
			);
			try
			{
				Files.move(
					temporary,
					path,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
				);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			try
			{
				Files.deleteIfExists(temporary);
			}
			catch (IOException ignored)
			{
				// Preserve the original failure.
			}
			throw new IllegalStateException("Unable to save the unlock-key vault", e);
		}
	}

	private void ensureAvailable()
	{
		if (!isAvailable())
		{
			throw new IllegalStateException(getUnavailableMessage());
		}
	}

	private int indexOf(String id)
	{
		int index = findIndex(id);
		if (index < 0)
		{
			throw new IllegalArgumentException("Saved unlock key no longer exists");
		}
		return index;
	}

	private int findIndex(String id)
	{
		Objects.requireNonNull(id, "id");
		for (int index = 0; index < entries.size(); index++)
		{
			if (entries.get(index).getId().equals(id))
			{
				return index;
			}
		}
		return -1;
	}

	private static byte[] toAscii(char[] key)
	{
		byte[] bytes = new byte[key.length];
		for (int index = 0; index < key.length; index++)
		{
			char character = key[index];
			if (character == 0 || character > 0x7f)
			{
				Arrays.fill(bytes, (byte) 0);
				throw new IllegalArgumentException("Unlock key contains invalid characters");
			}
			bytes[index] = (byte) character;
		}
		return bytes;
	}

	private static char[] fromAscii(byte[] bytes)
	{
		char[] key = new char[bytes.length];
		for (int index = 0; index < bytes.length; index++)
		{
			int value = bytes[index] & 0xff;
			if (value == 0 || value > 0x7f)
			{
				Arrays.fill(key, '\0');
				throw new IllegalStateException("The saved unlock key is damaged");
			}
			key[index] = (char) value;
		}
		return key;
	}

	private static final class VaultFile
	{
		private final int schemaVersion;
		private final List<SavedUnlockKey> entries;

		private VaultFile(int schemaVersion, List<SavedUnlockKey> entries)
		{
			this.schemaVersion = schemaVersion;
			this.entries = new ArrayList<>(entries);
		}
	}

	private static final class UnavailableProtector implements UnlockKeyProtector
	{
		@Override
		public boolean isAvailable()
		{
			return false;
		}

		@Override
		public String getUnavailableMessage()
		{
			return "Saved Unlock Keys are disabled in this context";
		}

		@Override
		public byte[] protect(byte[] plaintext)
		{
			throw new IllegalStateException(getUnavailableMessage());
		}

		@Override
		public byte[] unprotect(byte[] ciphertext)
		{
			throw new IllegalStateException(getUnavailableMessage());
		}
	}
}
