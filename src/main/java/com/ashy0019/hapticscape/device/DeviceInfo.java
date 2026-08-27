package com.ashy0019.hapticscape.device;

import java.util.Objects;

public final class DeviceInfo
{
	private final long index;
	private final String name;
	private final boolean vibrationSupported;

	public DeviceInfo(long index, String name, boolean vibrationSupported)
	{
		this.index = index;
		this.name = Objects.requireNonNull(name, "name");
		this.vibrationSupported = vibrationSupported;
	}

	public long getIndex()
	{
		return index;
	}

	public String getName()
	{
		return name;
	}

	public boolean isVibrationSupported()
	{
		return vibrationSupported;
	}

	@Override
	public String toString()
	{
		return vibrationSupported ? name : name + " (no vibration support)";
	}
}
