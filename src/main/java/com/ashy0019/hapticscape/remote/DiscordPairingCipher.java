package com.ashy0019.hapticscape.remote;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** Encrypts a one-use pairing code to a participant-generated ephemeral key. */
final class DiscordPairingCipher
{
	private static final int RSA_BITS = 2048;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
	private static final OAEPParameterSpec OAEP_SHA_256 = new OAEPParameterSpec(
		"SHA-256",
		"MGF1",
		MGF1ParameterSpec.SHA256,
		PSource.PSpecified.DEFAULT
	);

	private DiscordPairingCipher()
	{
	}

	static KeyPair generateKeyPair(SecureRandom random)
	{
		try
		{
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(
				new RSAKeyGenParameterSpec(RSA_BITS, RSAKeyGenParameterSpec.F4),
				random
			);
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException exception)
		{
			throw new IllegalStateException("Unable to protect the Discord connection request", exception);
		}
	}

	static String encodePublicKey(PublicKey publicKey)
	{
		validatePublicKey(publicKey);
		return ENCODER.encodeToString(publicKey.getEncoded());
	}

	static PublicKey decodePublicKey(String encoded)
	{
		try
		{
			byte[] bytes = DECODER.decode(encoded);
			if (!ENCODER.encodeToString(bytes).equals(encoded))
			{
				throw new IllegalArgumentException("Non-canonical public key");
			}
			PublicKey publicKey = KeyFactory.getInstance("RSA")
				.generatePublic(new X509EncodedKeySpec(bytes));
			validatePublicKey(publicKey);
			return publicKey;
		}
		catch (GeneralSecurityException | IllegalArgumentException exception)
		{
			throw new IllegalArgumentException("Invalid Discord participant key", exception);
		}
	}

	static String encrypt(String pairingCode, PublicKey publicKey, SecureRandom random)
	{
		RemotePairingCode.parse(pairingCode);
		validatePublicKey(publicKey);
		try
		{
			Cipher cipher = createCipher();
			cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA_256, random);
			return ENCODER.encodeToString(cipher.doFinal(
				pairingCode.getBytes(StandardCharsets.US_ASCII)
			));
		}
		catch (GeneralSecurityException exception)
		{
			throw new IllegalStateException("Unable to protect the Discord connection code", exception);
		}
	}

	static String decrypt(String encryptedCode, PrivateKey privateKey)
	{
		try
		{
			byte[] ciphertext = DECODER.decode(encryptedCode);
			if (!ENCODER.encodeToString(ciphertext).equals(encryptedCode))
			{
				throw new IllegalArgumentException("Non-canonical ciphertext");
			}
			Cipher cipher = createCipher();
			cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA_256);
			String pairingCode = new String(
				cipher.doFinal(ciphertext),
				StandardCharsets.US_ASCII
			);
			RemotePairingCode.parse(pairingCode);
			return pairingCode;
		}
		catch (GeneralSecurityException | IllegalArgumentException exception)
		{
			throw new IllegalArgumentException("Invalid encrypted Discord connection code", exception);
		}
	}

	private static Cipher createCipher() throws GeneralSecurityException
	{
		return Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
	}

	private static void validatePublicKey(PublicKey publicKey)
	{
		if (!(publicKey instanceof RSAPublicKey)
			|| ((RSAPublicKey) publicKey).getModulus().bitLength() != RSA_BITS
			|| !RSAKeyGenParameterSpec.F4.equals(
				((RSAPublicKey) publicKey).getPublicExponent()))
		{
			throw new IllegalArgumentException("Discord participant keys must use RSA-2048");
		}
	}
}
