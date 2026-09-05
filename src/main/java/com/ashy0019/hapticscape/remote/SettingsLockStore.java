package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
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

	List<SettingsLockProposal> load()
	{
		if (!Files.isRegularFile(path))
		{
			return Collections.emptyList();
		}
		try
		{
			String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			JsonObject root = new JsonParser().parse(json).getAsJsonObject();
			if (root.has("locks"))
			{
				SettingsLockDocument document = gson.fromJson(root, SettingsLockDocument.class);
				return document.validateAndGetLocks();
			}
			SettingsLockProposal proposal = gson.fromJson(root, SettingsLockProposal.class);
			if (proposal == null)
			{
				return Collections.emptyList();
			}
			proposal.validate();
			return Collections.singletonList(proposal);
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "Unable to read HapticScape settings lock", e);
			return Collections.emptyList();
		}
	}

	void save(List<SettingsLockProposal> proposals)
	{
		SettingsLockDocument document = new SettingsLockDocument(proposals);
		document.validateAndGetLocks();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(path.getParent());
			Files.write(temporary, gson.toJson(document).getBytes(StandardCharsets.UTF_8));
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
