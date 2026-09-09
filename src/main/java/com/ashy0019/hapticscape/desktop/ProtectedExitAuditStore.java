package com.ashy0019.hapticscape.desktop;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/** Durable marker used to carry an unauthorized-end flag across process restarts. */
public final class ProtectedExitAuditStore
{
	private static final String RUNNING = "running";
	private static final String PENDING = "unauthorizedEndPending";
	private static final String PROTECTED = "protected";

	private final Path path;
	private boolean running;
	private boolean pending;
	private boolean protectedExit;

	public ProtectedExitAuditStore(Path path)
	{
		this.path = Objects.requireNonNull(path, "path");
		load();
	}

	/** Marks this run active and converts an uncleared prior run into a pending flag. */
	public synchronized void beginRun(boolean protectionActive)
	{
		if (running && protectedExit)
		{
			pending = true;
		}
		running = true;
		protectedExit = protectionActive;
		persist();
	}

	public synchronized void setProtectionActive(boolean protectionActive)
	{
		protectedExit = protectionActive;
		persist();
	}

	public synchronized void markAuthorizedEnd()
	{
		running = false;
		protectedExit = false;
		persist();
	}

	public synchronized void markUnauthorizedEnd()
	{
		running = false;
		protectedExit = false;
		pending = true;
		persist();
	}

	public synchronized boolean hasPendingUnauthorizedEnd()
	{
		return pending;
	}

	public synchronized void clearPendingUnauthorizedEnd()
	{
		pending = false;
		persist();
	}

	private void load()
	{
		if (!Files.exists(path))
		{
			return;
		}
		Properties properties = new Properties();
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			properties.load(reader);
			running = Boolean.parseBoolean(properties.getProperty(RUNNING));
			pending = Boolean.parseBoolean(properties.getProperty(PENDING));
			protectedExit = Boolean.parseBoolean(properties.getProperty(PROTECTED));
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Unable to read protected-exit state", failure);
		}
	}

	private void persist()
	{
		Path absolute = path.toAbsolutePath();
		Path parent = absolute.getParent();
		if (parent == null)
		{
			throw new IllegalStateException("Protected-exit state has no parent directory");
		}
		Path temporary = null;
		try
		{
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, "protected-exit-", ".tmp");
			Properties properties = new Properties();
			properties.setProperty(RUNNING, Boolean.toString(running));
			properties.setProperty(PENDING, Boolean.toString(pending));
			properties.setProperty(PROTECTED, Boolean.toString(protectedExit));
			try (BufferedWriter writer = Files.newBufferedWriter(
				temporary,
				StandardCharsets.UTF_8))
			{
				properties.store(writer, "HapticScape protected-exit state");
			}
			try
			{
				Files.move(
					temporary,
					absolute,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
				);
			}
			catch (AtomicMoveNotSupportedException ignored)
			{
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
			temporary = null;
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Unable to write protected-exit state", failure);
		}
		finally
		{
			if (temporary != null)
			{
				try
				{
					Files.deleteIfExists(temporary);
				}
				catch (IOException ignored)
				{
					// Best-effort cleanup only.
				}
			}
		}
	}
}
