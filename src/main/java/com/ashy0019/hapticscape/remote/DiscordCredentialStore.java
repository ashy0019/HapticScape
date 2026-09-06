package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.runelite.client.RuneLite;

/** Stores one DPAPI-protected credential for the linked Discord account. */
public final class DiscordCredentialStore
{
	private static final Logger LOG = Logger.getLogger(DiscordCredentialStore.class.getName());
	private static final int SCHEMA_VERSION = 1;
	private static final long MAXIMUM_FILE_BYTES = 16L * 1024L;

	private final Gson gson;
	private final Path path;
	private final UnlockKeyProtector protector;
	private DiscordDeviceCredential credential;
	private String loadFailure;

	public DiscordCredentialStore(Gson gson)
	{
		this(
			gson,
			RuneLite.RUNELITE_DIR.toPath()
				.resolve("hapticscape")
				.resolve("discord-device.json"),
			new WindowsDpapiDiscordCredentialProtector()
		);
	}

	DiscordCredentialStore(
		Gson gson,
		Path path,
		UnlockKeyProtector protector)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.path = Objects.requireNonNull(path, "path");
		this.protector = Objects.requireNonNull(protector, "protector");
		if (protector.isAvailable())
		{
			load();
		}
	}

	public synchronized boolean isAvailable()
	{
		return protector.isAvailable() && loadFailure == null;
	}

	public synchronized String getUnavailableMessage()
	{
		if (!protector.isAvailable())
		{
			return protector.getUnavailableMessage();
		}
		return loadFailure == null ? "" : loadFailure;
	}

	synchronized Optional<DiscordDeviceCredential> get()
	{
		return Optional.ofNullable(credential);
	}

	synchronized void save(DiscordDeviceCredential next)
	{
		ensureAvailable();
		DiscordDeviceCredential required = Objects.requireNonNull(next, "credential");
		required.validate();
		byte[] plaintext = required.getSecret().getBytes(StandardCharsets.US_ASCII);
		byte[] protectedBytes = null;
		try
		{
			protectedBytes = protector.protect(plaintext);
			CredentialFile file = new CredentialFile(
				SCHEMA_VERSION,
				required.getUserId(),
				required.getDisplayName(),
				required.getRelayUrl(),
				Base64.getEncoder().encodeToString(protectedBytes)
			);
			persist(gson.toJson(file));
			credential = required;
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

	synchronized void clear()
	{
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try
		{
			Files.deleteIfExists(path);
			Files.deleteIfExists(temporary);
			credential = null;
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Unable to remove the Discord device link", exception);
		}
	}

	private void load()
	{
		if (!Files.isRegularFile(path))
		{
			return;
		}
		try
		{
			if (Files.size(path) > MAXIMUM_FILE_BYTES)
			{
				throw new IllegalArgumentException("Discord credential file is unexpectedly large");
			}
			CredentialFile file = gson.fromJson(
				new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
				CredentialFile.class
			);
			if (file == null || file.schemaVersion != SCHEMA_VERSION)
			{
				throw new IllegalArgumentException("Unsupported Discord credential format");
			}
			byte[] protectedBytes = Base64.getDecoder().decode(file.protectedSecret);
			byte[] plaintext = null;
			try
			{
				plaintext = protector.unprotect(protectedBytes);
				String secret = new String(plaintext, StandardCharsets.US_ASCII);
				credential = new DiscordDeviceCredential(
					file.userId,
					file.displayName,
					file.relayUrl,
					secret
				);
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
		catch (Exception exception)
		{
			loadFailure = "HapticScape could not read the existing Discord device link";
			credential = null;
			LOG.log(Level.WARNING, loadFailure, exception);
		}
	}

	private void persist(String json)
	{
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(path.getParent());
			Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
			try
			{
				Files.move(
					temporary,
					path,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
				);
			}
			catch (AtomicMoveNotSupportedException exception)
			{
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException exception)
		{
			try
			{
				Files.deleteIfExists(temporary);
			}
			catch (IOException ignored)
			{
				// Preserve the original failure.
			}
			throw new IllegalStateException("Unable to save the Discord device link", exception);
		}
	}

	private void ensureAvailable()
	{
		if (!isAvailable())
		{
			throw new IllegalStateException(getUnavailableMessage());
		}
	}

	private static final class CredentialFile
	{
		private final int schemaVersion;
		private final String userId;
		private final String displayName;
		private final String relayUrl;
		private final String protectedSecret;

		private CredentialFile(
			int schemaVersion,
			String userId,
			String displayName,
			String relayUrl,
			String protectedSecret)
		{
			this.schemaVersion = schemaVersion;
			this.userId = userId;
			this.displayName = displayName;
			this.relayUrl = relayUrl;
			this.protectedSecret = protectedSecret;
		}
	}
}
