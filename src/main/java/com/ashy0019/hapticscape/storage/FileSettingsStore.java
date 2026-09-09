package com.ashy0019.hapticscape.storage;

import com.ashy0019.hapticscape.remote.SettingsStore;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Durable standalone settings store backed by a UTF-8 properties file.
 *
 * <p>Writes replace the complete file through a sibling temporary file so a
 * process interruption cannot leave a partially-written settings document.
 * Secret material is intentionally stored elsewhere by dedicated protected
 * stores.</p>
 */
public final class FileSettingsStore implements SettingsStore
{
	private final Path settingsPath;
	private final Properties values = new Properties();
	private final CopyOnWriteArrayList<BiConsumer<String, String>> listeners =
		new CopyOnWriteArrayList<>();

	public FileSettingsStore(Path settingsPath)
	{
		this.settingsPath = Objects.requireNonNull(settingsPath, "settingsPath");
		load();
	}

	@Override
	public synchronized String get(String key)
	{
		Objects.requireNonNull(key, "key");
		return values.getProperty(key);
	}

	@Override
	public synchronized void set(String key, Object value)
	{
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
		values.setProperty(key, String.valueOf(value));
		persist();
		for (BiConsumer<String, String> listener : listeners)
		{
			listener.accept(key, String.valueOf(value));
		}
	}

	public void addChangeListener(BiConsumer<String, String> listener)
	{
		listeners.add(Objects.requireNonNull(listener, "listener"));
	}

	public Path getSettingsPath()
	{
		return settingsPath;
	}

	private void load()
	{
		if (!Files.exists(settingsPath))
		{
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8))
		{
			values.load(reader);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unable to read HapticScape settings from " + settingsPath, e);
		}
	}

	private void persist()
	{
		Path absolute = settingsPath.toAbsolutePath();
		Path parent = absolute.getParent();
		if (parent == null)
		{
			throw new IllegalStateException("Settings path has no parent directory: " + settingsPath);
		}

		Path temporary = null;
		try
		{
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, absolute.getFileName().toString() + ".", ".tmp");
			try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
			{
				values.store(writer, "HapticScape settings");
			}

			try
			{
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException ignored)
			{
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
			temporary = null;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unable to write HapticScape settings to " + settingsPath, e);
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
