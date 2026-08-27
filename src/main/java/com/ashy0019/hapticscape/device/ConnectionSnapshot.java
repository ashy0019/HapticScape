package com.ashy0019.hapticscape.device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ConnectionSnapshot
{
	private final ConnectionState state;
	private final String message;
	private final List<DeviceInfo> devices;

	public ConnectionSnapshot(ConnectionState state, String message, List<DeviceInfo> devices)
	{
		this.state = Objects.requireNonNull(state, "state");
		this.message = Objects.requireNonNull(message, "message");
		this.devices = Collections.unmodifiableList(new ArrayList<>(devices));
	}

	public static ConnectionSnapshot disconnected()
	{
		return new ConnectionSnapshot(ConnectionState.DISCONNECTED, "Disconnected", Collections.emptyList());
	}

	public ConnectionState getState()
	{
		return state;
	}

	public String getMessage()
	{
		return message;
	}

	public List<DeviceInfo> getDevices()
	{
		return devices;
	}
}
