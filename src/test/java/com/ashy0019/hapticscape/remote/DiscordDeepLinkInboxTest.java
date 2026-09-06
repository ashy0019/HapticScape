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
	@Test
	public void consumesAtomicLauncherRequestOnce() throws Exception
	{
		Path directory = Files.createTempDirectory("hapticscape-deep-links-");
		Path requestFile = directory.resolve("one.request");
		String encoded = "hapticscape://discord/accept"
			+ "?controller=123456789012345678"
			+ "&request=abcdefghijklmnop"
			+ "&token=abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";
		Files.write(requestFile, encoded.getBytes(StandardCharsets.UTF_8));
		DiscordDeepLinkInbox inbox = new DiscordDeepLinkInbox(directory);
		AtomicReference<DiscordDeepLinkRequest> delivered = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		inbox.setHandler(request ->
		{
			delivered.set(request);
			latch.countDown();
		});

		assertTrue(latch.await(2, TimeUnit.SECONDS));
		assertEquals("abcdefghijklmnop", delivered.get().getRequestId());
		assertFalse(Files.exists(requestFile));
	}

	@Test
	public void discardsMalformedLauncherRequest() throws Exception
	{
		Path directory = Files.createTempDirectory("hapticscape-deep-links-");
		Path requestFile = directory.resolve("bad.request");
		Files.write(requestFile, "hapticscape://discord/accept?token=bad"
			.getBytes(StandardCharsets.UTF_8));
		DiscordDeepLinkInbox inbox = new DiscordDeepLinkInbox(directory);
		AtomicReference<DiscordDeepLinkRequest> delivered = new AtomicReference<>();
		inbox.setHandler(delivered::set);
		inbox.scan();

		assertEquals(null, delivered.get());
		assertFalse(Files.exists(requestFile));
	}
}
