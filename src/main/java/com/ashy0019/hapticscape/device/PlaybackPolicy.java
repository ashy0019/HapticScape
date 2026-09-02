package com.ashy0019.hapticscape.device;

/**
 * Describes what a request may do when another finite pattern is active.
 */
public enum PlaybackPolicy
{
	DROP_IF_BUSY(false, false),
	INTERRUPT_LOWER(true, false),
	INTERRUPT_LOWER_OR_QUEUE(true, true);

	private final boolean interruptsLowerPriority;
	private final boolean queuesWhenBlocked;

	PlaybackPolicy(boolean interruptsLowerPriority, boolean queuesWhenBlocked)
	{
		this.interruptsLowerPriority = interruptsLowerPriority;
		this.queuesWhenBlocked = queuesWhenBlocked;
	}

	public boolean interruptsLowerPriority()
	{
		return interruptsLowerPriority;
	}

	public boolean queuesWhenBlocked()
	{
		return queuesWhenBlocked;
	}
}
