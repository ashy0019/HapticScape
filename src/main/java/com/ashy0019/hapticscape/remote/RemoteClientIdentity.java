package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/** Stable, non-secret identifier used only to associate persistent remote state. */
public final class RemoteClientIdentity
{
	private final String id;

	public RemoteClientIdentity(HapticScapeStoragePaths storagePaths)
	{
		this(Objects.requireNonNull(storagePaths, "storagePaths").getRemoteClientIdentityPath());
	}

	RemoteClientIdentity(Path path)
	{
		Objects.requireNonNull(path, "path");
		String loaded = load(path);
		if (loaded != null)
		{
			id = loaded;
			return;
		}
		id = UUID.randomUUID().toString();
		persist(path, id);
	}

	public String getId()
	{
		return id;
	}

	private static String load(Path path)
	{
		if (!Files.isRegularFile(path))
		{
			return null;
		}
		try
		{
			String value = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
			UUID.fromString(value);
			return value;
		}
		catch (IOException | IllegalArgumentException e)
		{
			return null;
		}
	}

	private static void persist(Path path, String value)
	{
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(path.getParent());
			Files.write(temporary, value.getBytes(StandardCharsets.UTF_8));
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
			throw new IllegalStateException("Unable to persist remote client identity", e);
		}
	}
}
