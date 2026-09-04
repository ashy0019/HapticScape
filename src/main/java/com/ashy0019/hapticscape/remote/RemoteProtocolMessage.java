package com.ashy0019.hapticscape.remote;

final class RemoteProtocolMessage
{
	private final RemoteMessageType type;
	private final long version;
	private final String payload;

	RemoteProtocolMessage(RemoteMessageType type, long version, String payload)
	{
		this.type = type;
		this.version = version;
		this.payload = payload;
	}

	RemoteMessageType getType()
	{
		return type;
	}

	long getVersion()
	{
		return version;
	}

	String getPayload()
	{
		return payload;
	}
}
