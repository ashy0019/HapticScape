package com.ashy0019.hapticscape.device;

/**
 * Relative ownership of the single haptic output channel.
 */
public enum HapticPriority
{
	ROUTINE(10),
	GENERIC(20),
	PROGRESS(30),
	GAMEPLAY(40),
	DIRECT(50),
	MANUAL(60),
	CEREMONY(70),
	URGENT(80),
	CRITICAL(90);

	private final int rank;

	HapticPriority(int rank)
	{
		this.rank = rank;
	}

	public boolean outranks(HapticPriority other)
	{
		return rank > other.rank;
	}

	int getRank()
	{
		return rank;
	}
}
