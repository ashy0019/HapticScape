package com.ashy0019.hapticscape.remote;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Derives a role-specific, session-scoped credential used only to replace a
 * stale relay socket after a network interruption.
 */
final class RemoteReconnectSlot
{
	private static final String ALGORITHM = "HmacSHA256";
	private static final String DOMAIN = "HapticScape reconnect slot v1:";
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private RemoteReconnectSlot()
	{
	}

	static String derive(String sessionKey, RemoteRole role)
	{
		String encodedKey = Objects.requireNonNull(sessionKey, "sessionKey").trim();
		RemoteRole requiredRole = Objects.requireNonNull(role, "role");
		if (requiredRole == RemoteRole.NONE)
		{
			throw new IllegalArgumentException("Reconnect slot requires an active remote role");
		}

		byte[] keyBytes = DECODER.decode(encodedKey);
		try
		{
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
			byte[] slotBytes = mac.doFinal(
				(DOMAIN + requiredRole.name().toLowerCase()).getBytes(StandardCharsets.US_ASCII)
			);
			return ENCODER.encodeToString(slotBytes);
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException("Unable to derive remote reconnect slot", e);
		}
		finally
		{
			Arrays.fill(keyBytes, (byte) 0);
		}
	}
}
