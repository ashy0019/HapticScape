package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.LootReceivedEvent;
import java.util.Objects;

/** Source-neutral policy for deciding whether received loot is valuable. */
final class LootAlertDecision
{
	private LootAlertDecision()
	{
	}

	static boolean shouldAlert(LootReceivedEvent event, long minimumValue)
	{
		LootReceivedEvent required = Objects.requireNonNull(event, "event");
		long threshold = Math.max(0L, minimumValue);
		return required.getStackCount() > 0 && required.getTotalValue() >= threshold;
	}
}
