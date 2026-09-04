package com.ashy0019.hapticscape.remote;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class RemoteInvitation
{
	private static final String PREFIX = "HSR1";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final String relayUrl;
	private final String roomId;
	private final String key;

	public RemoteInvitation(String relayUrl, String roomId, String key)
	{
		this.relayUrl = requireText(relayUrl, "relay URL");
		this.roomId = requireText(roomId, "room ID");
		this.key = requireText(key, "session key");
	}

	public String getRelayUrl()
	{
		return relayUrl;
	}

	public String getRoomId()
	{
		return roomId;
	}

	public String getKey()
	{
		return key;
	}

	public String encode()
	{
		String encodedRelay = ENCODER.encodeToString(relayUrl.getBytes(StandardCharsets.UTF_8));
		return PREFIX + "." + encodedRelay + "." + roomId + "." + key;
	}

	public static RemoteInvitation parse(String encoded)
	{
		String value = requireText(encoded, "invitation");
		String[] fields = value.split("\\.", -1);
		if (fields.length != 4 || !PREFIX.equals(fields[0]))
		{
			throw new IllegalArgumentException("Invalid HapticScape remote invitation");
		}

		try
		{
			String relayUrl = new String(DECODER.decode(fields[1]), StandardCharsets.UTF_8);
			return new RemoteInvitation(relayUrl, fields[2], fields[3]);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalArgumentException("Invalid HapticScape remote invitation", e);
		}
	}

	private static String requireText(String value, String name)
	{
		String result = Objects.requireNonNull(value, name).trim();
		if (result.isEmpty())
		{
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return result;
	}
}
