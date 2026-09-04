package com.ashy0019.hapticscape.remote;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password verifier that can be persisted or sent through an encrypted remote
 * session without retaining the password itself.
 */
public final class SettingsLockProposal
{
	static final int SCHEMA_VERSION = 1;
	static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	static final int ITERATIONS = 310_000;
	static final int KEY_LENGTH_BITS = 256;
	static final int SALT_LENGTH_BYTES = 16;
	public static final int MINIMUM_PASSWORD_LENGTH = 10;

	private final int schemaVersion;
	private final String proposalId;
	private final String algorithm;
	private final int iterations;
	private final String salt;
	private final String verifier;

	private SettingsLockProposal(
		int schemaVersion,
		String proposalId,
		String algorithm,
		int iterations,
		String salt,
		String verifier)
	{
		this.schemaVersion = schemaVersion;
		this.proposalId = proposalId;
		this.algorithm = algorithm;
		this.iterations = iterations;
		this.salt = salt;
		this.verifier = verifier;
	}

	public static SettingsLockProposal create(char[] password)
	{
		Objects.requireNonNull(password, "password");
		if (password.length < MINIMUM_PASSWORD_LENGTH)
		{
			throw new IllegalArgumentException(
				"Lock password must contain at least " + MINIMUM_PASSWORD_LENGTH + " characters"
			);
		}
		byte[] saltBytes = new byte[SALT_LENGTH_BYTES];
		new SecureRandom().nextBytes(saltBytes);
		byte[] derived = derive(password, saltBytes, ITERATIONS);
		try
		{
			Base64.Encoder encoder = Base64.getEncoder();
			return new SettingsLockProposal(
				SCHEMA_VERSION,
				UUID.randomUUID().toString(),
				ALGORITHM,
				ITERATIONS,
				encoder.encodeToString(saltBytes),
				encoder.encodeToString(derived)
			);
		}
		finally
		{
			Arrays.fill(derived, (byte) 0);
			Arrays.fill(saltBytes, (byte) 0);
		}
	}

	public String getProposalId()
	{
		return proposalId;
	}

	public void validate()
	{
		if (schemaVersion != SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported settings-lock format");
		}
		if (!ALGORITHM.equals(algorithm))
		{
			throw new IllegalArgumentException("Unsupported settings-lock algorithm");
		}
		if (iterations < 100_000 || iterations > 1_000_000)
		{
			throw new IllegalArgumentException("Invalid settings-lock work factor");
		}
		try
		{
			UUID.fromString(Objects.requireNonNull(proposalId, "proposalId"));
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			throw new IllegalArgumentException("Invalid settings-lock proposal ID", e);
		}
		byte[] saltBytes = decode(salt, "salt");
		byte[] verifierBytes = decode(verifier, "verifier");
		try
		{
			if (saltBytes.length < SALT_LENGTH_BYTES || saltBytes.length > 64)
			{
				throw new IllegalArgumentException("Invalid settings-lock salt");
			}
			if (verifierBytes.length != KEY_LENGTH_BITS / 8)
			{
				throw new IllegalArgumentException("Invalid settings-lock verifier");
			}
		}
		finally
		{
			Arrays.fill(saltBytes, (byte) 0);
			Arrays.fill(verifierBytes, (byte) 0);
		}
	}

	boolean verifies(char[] password)
	{
		Objects.requireNonNull(password, "password");
		validate();
		byte[] saltBytes = decode(salt, "salt");
		byte[] expected = decode(verifier, "verifier");
		byte[] actual = derive(password, saltBytes, iterations);
		try
		{
			return MessageDigest.isEqual(expected, actual);
		}
		finally
		{
			Arrays.fill(saltBytes, (byte) 0);
			Arrays.fill(expected, (byte) 0);
			Arrays.fill(actual, (byte) 0);
		}
	}

	private static byte[] derive(char[] password, byte[] salt, int iterations)
	{
		PBEKeySpec specification = new PBEKeySpec(
			password,
			salt,
			iterations,
			KEY_LENGTH_BITS
		);
		try
		{
			return SecretKeyFactory.getInstance(ALGORITHM)
				.generateSecret(specification)
				.getEncoded();
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException("Unable to derive settings-lock verifier", e);
		}
		finally
		{
			specification.clearPassword();
		}
	}

	private static byte[] decode(String value, String name)
	{
		try
		{
			return Base64.getDecoder().decode(Objects.requireNonNull(value, name));
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			throw new IllegalArgumentException("Invalid settings-lock " + name, e);
		}
	}
}
