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

/** Watches the per-user launcher inbox for validated HapticScape protocol requests. */
public final class DiscordDeepLinkInbox implements AutoCloseable
{
	@FunctionalInterface
	public interface Handler
	{
		/**
		 * @return true once this process has accepted ownership of the request;
		 * false when another live process should be allowed to retry it.
		 */
		boolean handle(DiscordDeepLinkRequest request);
	}

	private static final Duration MAXIMUM_AGE = Duration.ofMinutes(5);
	private static final long MAXIMUM_FILE_BYTES = 2048;

	private final Path inboxPath;
	private final ScheduledExecutorService watcher;
	private volatile Handler handler;
	private volatile boolean started;
	private volatile boolean closed;

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
		if (closed)
		{
			throw new IllegalStateException("Discord deep-link inbox is closed");
		}
		if (started)
		{
			return;
		}
		started = true;
		watcher.scheduleWithFixedDelay(this::scanSafely, 0, 250, TimeUnit.MILLISECONDS);
	}

	public void setHandler(Handler handler)
	{
		synchronized (this)
		{
			if (closed)
			{
				return;
			}
			this.handler = handler;
		}
		if (handler != null)
		{
			watcher.execute(this::scanSafely);
		}
	}

	void scan() throws IOException
	{
		Handler current = handler;
		if (closed || current == null || !Files.isDirectory(inboxPath))
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
				if (closed || current != handler)
				{
					return;
				}
				consume(file, current);
			}
		}
	}

	private void consume(Path file, Handler current)
	{
		boolean delete = false;
		try
		{
			FileTime modified = Files.getLastModifiedTime(file);
			if (modified.toInstant().isBefore(Instant.now().minus(MAXIMUM_AGE))
				|| Files.size(file) > MAXIMUM_FILE_BYTES)
			{
				delete = true;
				return;
			}
			String encoded = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			DiscordDeepLinkRequest request;
			try
			{
				request = DiscordDeepLinkRequest.parse(encoded);
			}
			catch (RuntimeException invalid)
			{
				delete = true;
				return;
			}
			if (closed || current != handler)
			{
				return;
			}
			delete = current.handle(request);
		}
		catch (IOException ignored)
		{
			// Atomic launcher requests can be retried after transient filesystem failures.
		}
		catch (RuntimeException ignored)
		{
			// A handler that is shutting down must not consume the durable request.
		}
		finally
		{
			if (delete)
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
		if (closed)
		{
			return;
		}
		try
		{
			scan();
		}
		catch (IOException ignored)
		{
			// The inbox is optional until the packaged launcher creates it.
		}
	}

	@Override
	public synchronized void close()
	{
		if (closed)
		{
			return;
		}
		closed = true;
		handler = null;
		watcher.shutdownNow();
	}
}
