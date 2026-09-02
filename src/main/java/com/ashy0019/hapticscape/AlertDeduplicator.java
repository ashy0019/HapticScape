package com.ashy0019.hapticscape;

import java.util.concurrent.TimeUnit;

public final class AlertDeduplicator
{
	public static final long GENERIC_DELAY_MILLIS = 250;
	private static final long SUPPRESSION_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(750);

	private volatile long lastSpecificAlertNanos = Long.MIN_VALUE;

	public void recordSpecificAlert(long nowNanos)
	{
		lastSpecificAlertNanos = nowNanos;
	}

	public boolean shouldSuppressGeneric(long notificationNanos, long nowNanos)
	{
		long specific = lastSpecificAlertNanos;
		return specific != Long.MIN_VALUE
			&& specific >= notificationNanos - SUPPRESSION_WINDOW_NANOS
			&& specific <= nowNanos;
	}

	public void reset()
	{
		lastSpecificAlertNanos = Long.MIN_VALUE;
	}
}
