package com.ashy0019.hapticscape;

public enum CustomPatternSlot
{
	I("Custom 1"),
	II("Custom 2"),
	III("Custom 3"),
	IV("Custom 4");

	private final String defaultName;

	CustomPatternSlot(String defaultName)
	{
		this.defaultName = defaultName;
	}

	public String getDefaultName()
	{
		return defaultName;
	}

	@Override
	public String toString()
	{
		return defaultName;
	}
}
