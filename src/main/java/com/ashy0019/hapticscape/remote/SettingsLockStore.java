package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SettingsLockStore
{
	private static final Logger LOG = Logger.getLogger(SettingsLockStore.class.getName());

	private final Gson gson;
	private final Path path;

	SettingsLockStore(Gson gson, Path path)
	{
		this.gson = gson;
		this.path = path;
	}

	Optional<SettingsLockProposal> load()
	{
		if (!Files.isRegularFile(path))
		{
			return Optional.empty();
		}
		try
		{
			String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			SettingsLockProposal proposal = gson.fromJson(json, SettingsLockProposal.class);
			if (proposal == null)
			{
				return Optional.empty();
			}
			proposal.validate();
			return Optional.of(proposal);
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "Unable to read HapticScape settings lock", e);
			return Optional.empty();
		}
	}

	void save(SettingsLockProposal proposal)
	{
		proposal.validate();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(path.getParent());
			Files.write(temporary, gson.toJson(proposal).getBytes(StandardCharsets.UTF_8));
			moveIntoPlace(temporary);
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
			throw new IllegalStateException("Unable to save HapticScape settings lock", e);
		}
	}

	void clear()
	{
		try
		{
			Files.deleteIfExists(path);
			Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".tmp"));
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unable to clear HapticScape settings lock", e);
		}
	}

	private void moveIntoPlace(Path temporary) throws IOException
	{
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
}
