package com.ashy0019.hapticscape.clicker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClickerServiceTest
{
	@Test
	public void disabledServiceIgnoresClicks()
	{
		AtomicInteger plays = new AtomicInteger();
		ClickerService service = new ClickerService(
			gainDb -> plays.incrementAndGet(),
			new ClickerSettings(false, 70)
		);

		try
		{
			service.click();
			assertEquals(0, plays.get());
		}
		finally
		{
			service.close();
		}
	}

	@Test
	public void enabledServiceDispatchesClickOnDaemonThread() throws Exception
	{
		CountDownLatch played = new CountDownLatch(1);
		AtomicBoolean daemon = new AtomicBoolean();
		ClickerService service = new ClickerService(
			gainDb ->
			{
				daemon.set(Thread.currentThread().isDaemon());
				played.countDown();
			},
			new ClickerSettings(true, 70)
		);

		try
		{
			service.click();
			assertTrue(played.await(1, TimeUnit.SECONDS));
			assertTrue(daemon.get());
		}
		finally
		{
			service.close();
		}
	}

	@Test
	public void playbackFailureDoesNotEscapeWorker() throws Exception
	{
		CountDownLatch attempted = new CountDownLatch(1);
		ClickerService service = new ClickerService(
			gainDb ->
			{
				attempted.countDown();
				throw new IllegalStateException("No audio device");
			},
			new ClickerSettings(true, 70)
		);

		try
		{
			service.click();
			assertTrue(attempted.await(1, TimeUnit.SECONDS));
		}
		finally
		{
			service.close();
		}
	}

	@Test
	public void saturationDropsInsteadOfQueueing() throws Exception
	{
		CountDownLatch entered = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(2);
		AtomicInteger plays = new AtomicInteger();
		ClickerService service = new ClickerService(
			gainDb ->
			{
				plays.incrementAndGet();
				entered.countDown();
				try
				{
					release.await();
				}
				finally
				{
					completed.countDown();
				}
			},
			new ClickerSettings(true, 70)
		);

		try
		{
			service.click();
			service.click();
			assertTrue(entered.await(1, TimeUnit.SECONDS));
			service.click();
			assertEquals(2, plays.get());
			release.countDown();
			assertTrue(completed.await(1, TimeUnit.SECONDS));
			assertEquals(2, plays.get());
		}
		finally
		{
			release.countDown();
			service.close();
		}
	}

	@Test
	public void closeRejectsFutureClicks()
	{
		AtomicInteger plays = new AtomicInteger();
		ClickerService service = new ClickerService(
			gainDb -> plays.incrementAndGet(),
			new ClickerSettings(true, 70)
		);

		service.close();
		service.click();

		assertEquals(0, plays.get());
	}

	@Test
	public void zeroVolumeIsSilent()
	{
		AtomicInteger plays = new AtomicInteger();
		ClickerService service = new ClickerService(
			gainDb -> plays.incrementAndGet(),
			new ClickerSettings(true, 0)
		);

		try
		{
			service.click();
			assertEquals(0, plays.get());
		}
		finally
		{
			service.close();
		}
	}
}
