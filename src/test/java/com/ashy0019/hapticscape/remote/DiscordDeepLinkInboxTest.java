package com.ashy0019.hapticscape.remote;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiscordDeepLinkInboxTest
{
	private static final String VALID_REQUEST = "hapticscape://discord/accept"
		+ "?controller=123456789012345678"
		+ "&request=abcdefghijklmnop"
		+ "&token=abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";

	@Test
	public void consumesAtomicLauncherRequestOnce() throws Exception
	{
		Path directory = Files.createTempDirectory("hapticscape-deep-links-");
		Path requestFile = directory.resolve("one.request");
		Files.write(requestFile, VALID_REQUEST.getBytes(StandardCharsets.UTF_8));
		AtomicReference<DiscordDeepLinkRequest> delivered = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		try (DiscordDeepLinkInbox inbox = new DiscordDeepLinkInbox(directory))
		{
			inbox.setHandler(request ->
			{
				delivered.set(request);
				latch.countDown();
				return true;
			});

			assertTrue(latch.await(2, TimeUnit.SECONDS));
			assertEquals("abcdefghijklmnop", delivered.get().getRequestId());
			assertTrue(waitForMissing(requestFile, 2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void retainsRequestWhenHandlerCannotAcceptItYet() throws Exception
	{
		Path directory = Files.createTempDirectory("hapticscape-deep-links-");
		Path requestFile = directory.resolve("retry.request");
		Files.write(requestFile, VALID_REQUEST.getBytes(StandardCharsets.UTF_8));
		try (DiscordDeepLinkInbox inbox = new DiscordDeepLinkInbox(directory))
		{
			inbox.setHandler(request -> false);
			inbox.scan();
			assertTrue(Files.exists(requestFile));

			inbox.setHandler(request -> true);
			inbox.scan();
			assertFalse(Files.exists(requestFile));
		}
	}

	@Test
	public void closeStopsDeliveryWithoutDeletingPendingRequest() throws Exception
	{
		Path directory = Files.createTempDirectory("hapticscape-deep-links-");
		Path requestFile = directory.resolve("closing.request");
		Files.write(requestFile, VALID_REQUEST.getBytes(StandardCharsets.UTF_8));
		DiscordDeepLinkInbox inbox = new DiscordDeepLinkInbox(directory);
		inbox.close();
		inbox.setHandler(request -> true);
		inbox.scan();

		assertTrue(Files.exists(requestFile));
	}

	@Test
	public void discardsMalformedLauncherRequest() throws Exception
	{
		Path directory = Files.createTempDirectory("hapticscape-deep-links-");
		Path requestFile = directory.resolve("bad.request");
		Files.write(requestFile, "hapticscape://discord/accept?token=bad"
			.getBytes(StandardCharsets.UTF_8));
		AtomicReference<DiscordDeepLinkRequest> delivered = new AtomicReference<>();
		try (DiscordDeepLinkInbox inbox = new DiscordDeepLinkInbox(directory))
		{
			inbox.setHandler(request ->
			{
				delivered.set(request);
				return true;
			});
			inbox.scan();

			assertEquals(null, delivered.get());
			assertFalse(Files.exists(requestFile));
		}
	}
	private static boolean waitForMissing(Path path, long timeout, TimeUnit unit) throws InterruptedException
	{
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		do
		{
			if (!Files.exists(path))
			{
				return true;
			}
			Thread.sleep(10);
		}
		while (System.nanoTime() < deadline);
		return !Files.exists(path);
	}

}
