package com.ashy0019.hapticscape.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Watches the per-user launcher inbox for validated HapticScape protocol requests. */
public final class DiscordDeepLinkInbox
{
	private static final Duration MAXIMUM_AGE = Duration.ofMinutes(5);
	private static final long MAXIMUM_FILE_BYTES = 2048;

	private final Path inboxPath;
	private final ScheduledExecutorService watcher;
	private volatile Consumer<DiscordDeepLinkRequest> handler;
	private volatile boolean started;

	public DiscordDeepLinkInbox(Path inboxPath)
	{
		this.inboxPath = inboxPath;
		this.watcher = Executors.newSingleThreadScheduledExecutor(runnable ->
		{
			Thread thread = new Thread(runnable, "hapticscape-deep-link-inbox");
			thread.setDaemon(true);
			return thread;
		});
	}

	public synchronized void start()
	{
		if (started)
		{
			return;
		}
		started = true;
		watcher.scheduleWithFixedDelay(this::scanSafely, 0, 250, TimeUnit.MILLISECONDS);
	}

	public void setHandler(Consumer<DiscordDeepLinkRequest> handler)
	{
		this.handler = handler;
		if (handler != null)
		{
			watcher.execute(this::scanSafely);
		}
	}

	void scan() throws IOException
	{
		Consumer<DiscordDeepLinkRequest> current = handler;
		if (current == null || !Files.isDirectory(inboxPath))
		{
			return;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(inboxPath, "*.request"))
		{
			java.util.List<Path> ordered = new java.util.ArrayList<>();
			for (Path file : files)
			{
				ordered.add(file);
			}
			ordered.sort(Comparator.comparing(this::lastModifiedSafely));
			for (Path file : ordered)
			{
				consume(file, current);
			}
		}
	}

	private void consume(Path file, Consumer<DiscordDeepLinkRequest> current)
	{
		try
		{
			FileTime modified = Files.getLastModifiedTime(file);
			if (modified.toInstant().isBefore(Instant.now().minus(MAXIMUM_AGE))
				|| Files.size(file) > MAXIMUM_FILE_BYTES)
			{
				return;
			}
			String encoded = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			DiscordDeepLinkRequest request = DiscordDeepLinkRequest.parse(encoded);
			Files.deleteIfExists(file);
			current.accept(request);
		}
		catch (IOException | RuntimeException ignored)
		{
			// Invalid, incomplete, and stale launcher requests grant no authority.
		}
		finally
		{
			try
			{
				Files.deleteIfExists(file);
			}
			catch (IOException ignored)
			{
				// A later scan can retry cleanup.
			}
		}
	}

	private FileTime lastModifiedSafely(Path file)
	{
		try
		{
			return Files.getLastModifiedTime(file);
		}
		catch (IOException ignored)
		{
			return FileTime.fromMillis(Long.MAX_VALUE);
		}
	}

	private void scanSafely()
	{
		try
		{
			scan();
		}
		catch (IOException ignored)
		{
			// The inbox is optional until the packaged launcher creates it.
		}
	}
}
