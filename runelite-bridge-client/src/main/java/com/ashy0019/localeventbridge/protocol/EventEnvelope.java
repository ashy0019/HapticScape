package com.ashy0019.localeventbridge.protocol;

import com.google.gson.JsonObject;
import java.util.Objects;

/**
 * Versioned, source-neutral event envelope. The payload contains only
 * event-specific fields; source and type live at the envelope level.
 */
public final class EventEnvelope
{
	private final String protocol;
	private final int version;
	private final String source;
	private final String type;
	private final JsonObject payload;

	public EventEnvelope(
		String protocol,
		int version,
		String source,
		String type,
		JsonObject payload)
	{
		this.protocol = requireIdentifier(protocol, "protocol");
		if (version <= 0)
		{
			throw new IllegalArgumentException("version must be positive");
		}
		this.version = version;
		this.source = requireIdentifier(source, "source");
		this.type = requireIdentifier(type, "type");
		this.payload = Objects.requireNonNull(payload, "payload");
	}

	public String getProtocol()
	{
		return protocol;
	}

	public int getVersion()
	{
		return version;
	}

	public String getSource()
	{
		return source;
	}

	public String getType()
	{
		return type;
	}

	public JsonObject getPayload()
	{
		return payload;
	}

	private static String requireIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}
}
