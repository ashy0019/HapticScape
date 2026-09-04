package com.ashy0019.hapticscape.remote;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class RemoteCrypto
{
	private static final String PREFIX = "E1";
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final SecureRandom random = new SecureRandom();
	private final SecretKeySpec key;

	RemoteCrypto(String encodedKey)
	{
		byte[] keyBytes;
		try
		{
			keyBytes = DECODER.decode(encodedKey);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalArgumentException("Invalid remote session key", e);
		}
		if (keyBytes.length != 32)
		{
			throw new IllegalArgumentException("Remote session key must be 256 bits");
		}
		key = new SecretKeySpec(keyBytes, "AES");
	}

	String encrypt(String plaintext)
	{
		try
		{
			byte[] nonce = new byte[NONCE_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return PREFIX + "." + ENCODER.encodeToString(nonce)
				+ "." + ENCODER.encodeToString(ciphertext);
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException("Unable to encrypt remote message", e);
		}
	}

	String decrypt(String encoded)
	{
		String[] fields = encoded == null ? new String[0] : encoded.split("\\.", -1);
		if (fields.length != 3 || !PREFIX.equals(fields[0]))
		{
			throw new IllegalArgumentException("Invalid encrypted remote message");
		}
		try
		{
			byte[] nonce = DECODER.decode(fields[1]);
			byte[] ciphertext = DECODER.decode(fields[2]);
			if (nonce.length != NONCE_BYTES)
			{
				throw new IllegalArgumentException("Invalid remote message nonce");
			}
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e)
		{
			throw new IllegalArgumentException("Unable to decrypt remote message", e);
		}
	}
}
