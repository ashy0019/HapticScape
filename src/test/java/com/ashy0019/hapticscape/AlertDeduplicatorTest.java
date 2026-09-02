package com.ashy0019.hapticscape;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlertDeduplicatorTest
{
	@Test
	public void specificAlertSuppressesGenericRegardlessOfEventOrdering()
	{
		AlertDeduplicator deduplicator = new AlertDeduplicator();
		long notification = TimeUnit.SECONDS.toNanos(10);
		deduplicator.recordSpecificAlert(notification + TimeUnit.MILLISECONDS.toNanos(100));

		assertTrue(deduplicator.shouldSuppressGeneric(
			notification,
			notification + TimeUnit.MILLISECONDS.toNanos(250)
		));
	}

	@Test
	public void oldSpecificAlertDoesNotSuppressUnrelatedNotification()
	{
		AlertDeduplicator deduplicator = new AlertDeduplicator();
		long notification = TimeUnit.SECONDS.toNanos(10);
		deduplicator.recordSpecificAlert(notification - TimeUnit.SECONDS.toNanos(2));

		assertFalse(deduplicator.shouldSuppressGeneric(
			notification,
			notification + TimeUnit.MILLISECONDS.toNanos(250)
		));
	}
}
