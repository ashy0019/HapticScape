package com.ashy0019.hapticscape;

public enum AlertBehavior
{
	USE_GENERIC("Use generic"),
	CUSTOM("Custom profile"),
	OFF("Off");

	private final String displayName;

	AlertBehavior(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
