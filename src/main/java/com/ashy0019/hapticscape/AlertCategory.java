package com.ashy0019.hapticscape;

public enum AlertCategory
{
	GENERIC_NOTIFICATION("Generic notification", false),
	DIRECT_MESSAGE("Direct message", false),
	TRADE_REQUEST("Trade request", false),
	LOW_HITPOINTS("Low hitpoints", true),
	LOW_PRAYER("Low prayer", true);

	private final String displayName;
	private final boolean thresholdBased;

	AlertCategory(String displayName, boolean thresholdBased)
	{
		this.displayName = displayName;
		this.thresholdBased = thresholdBased;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public boolean isThresholdBased()
	{
		return thresholdBased;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
