package com.ashy0019.hapticscape.remote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Short-lived bearer secret used to publish and redeem an encrypted invitation. */
public final class RemotePairingCode
{
	private static final String PREFIX = "HSP1";
	private static final int SECRET_BYTES = 32;
	private static final int LOCATOR_BYTES = 9;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final byte[] secret;

	private RemotePairingCode(byte[] secret)
	{
		if (secret.length != SECRET_BYTES)
		{
			throw new IllegalArgumentException("Invalid HapticScape connection code");
		}
		this.secret = Arrays.copyOf(secret, secret.length);
	}

	public static RemotePairingCode generate()
	{
		byte[] secret = new byte[SECRET_BYTES];
		new SecureRandom().nextBytes(secret);
		try
		{
			return new RemotePairingCode(secret);
		}
		finally
		{
			Arrays.fill(secret, (byte) 0);
		}
	}

	public static RemotePairingCode parse(String encoded)
	{
		String value = Objects.requireNonNull(encoded, "connection code").trim();
		String[] fields = value.split("\\.", -1);
		if (fields.length != 2 || !PREFIX.equals(fields[0]))
		{
			throw new IllegalArgumentException("Invalid HapticScape connection code");
		}

		byte[] secret;
		try
		{
			secret = DECODER.decode(fields[1]);
		}
		catch (IllegalArgumentException exception)
		{
			throw new IllegalArgumentException("Invalid HapticScape connection code", exception);
		}
		try
		{
			if (!ENCODER.encodeToString(secret).equals(fields[1]))
			{
				throw new IllegalArgumentException("Invalid HapticScape connection code");
			}
			return new RemotePairingCode(secret);
		}
		finally
		{
			Arrays.fill(secret, (byte) 0);
		}
	}

	public String encode()
	{
		return PREFIX + "." + ENCODER.encodeToString(secret);
	}

	String locator()
	{
		byte[] digest = digest("locator");
		byte[] locator = Arrays.copyOf(digest, LOCATOR_BYTES);
		try
		{
			return ENCODER.encodeToString(locator);
		}
		finally
		{
			Arrays.fill(digest, (byte) 0);
			Arrays.fill(locator, (byte) 0);
		}
	}

	String redeemProof()
	{
		return proof("redeem");
	}

	String cancelProof()
	{
		return proof("cancel");
	}

	String encryptionKey()
	{
		return ENCODER.encodeToString(secret);
	}

	private String proof(String purpose)
	{
		byte[] digest = digest(purpose);
		try
		{
			return ENCODER.encodeToString(digest);
		}
		finally
		{
			Arrays.fill(digest, (byte) 0);
		}
	}

	private byte[] digest(String purpose)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(
				("HapticScape pairing " + purpose + ":")
					.getBytes(StandardCharsets.UTF_8)
			);
			return digest.digest(secret);
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	@Override
	public String toString()
	{
		return encode();
	}
}
