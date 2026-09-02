package com.ashy0019.hapticscape.update;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
public final class UpdatePreferencesStore implements AutoCloseable
{
	private final Gson gson;
	private final Path settingsPath;
	private final ExecutorService executor;
	private volatile UpdatePreferences current = UpdatePreferences.defaults();

	public UpdatePreferencesStore(Gson gson)
	{
		this(
			gson,
			RuneLite.RUNELITE_DIR.toPath()
				.resolve("hapticscape")
				.resolve("updater-settings.json"));
	}

	UpdatePreferencesStore(Gson gson, Path settingsPath)
	{
		this.gson = gson;
		this.settingsPath = settingsPath;
		this.executor = Executors.newSingleThreadExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-update-settings");
			thread.setDaemon(true);
			return thread;
		});
	}

	public CompletableFuture<UpdatePreferences> load()
	{
		return CompletableFuture.supplyAsync(() ->
		{
			UpdatePreferences loaded = loadNow();
			current = loaded;
			return loaded;
		}, executor);
	}

	public UpdatePreferences getCurrent()
	{
		return current;
	}

	public void save(UpdatePreferences preferences)
	{
		current = preferences;
		executor.execute(() -> saveNow(preferences));
	}

	private UpdatePreferences loadNow()
	{
		if (!Files.isRegularFile(settingsPath))
		{
			return UpdatePreferences.defaults();
		}
		try
		{
			String json = new String(Files.readAllBytes(settingsPath), StandardCharsets.UTF_8);
			UpdatePreferences loaded = gson.fromJson(json, UpdatePreferences.class);
			return loaded == null ? UpdatePreferences.defaults() : loaded;
		}
		catch (Exception exception)
		{
			log.warn("Unable to read HapticScape updater preferences; using defaults", exception);
			return UpdatePreferences.defaults();
		}
	}

	private void saveNow(UpdatePreferences preferences)
	{
		Path temporaryPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(settingsPath.getParent());
			Files.write(
				temporaryPath,
				gson.toJson(preferences).getBytes(StandardCharsets.UTF_8));
			try
			{
				Files.move(
					temporaryPath,
					settingsPath,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException exception)
			{
				Files.move(
					temporaryPath,
					settingsPath,
					StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException exception)
		{
			log.warn("Unable to save HapticScape updater preferences", exception);
			try
			{
				Files.deleteIfExists(temporaryPath);
			}
			catch (IOException ignored)
			{
				// Preserve the original failure in the log.
			}
		}
	}

	@Override
	public void close()
	{
		executor.shutdown();
		try
		{
			if (!executor.awaitTermination(2, TimeUnit.SECONDS))
			{
				executor.shutdownNow();
			}
		}
		catch (InterruptedException exception)
		{
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
