package com.ashy0019.hapticscape.remote;

import java.security.KeyPair;
import java.security.SecureRandom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DiscordPairingCipherTest
{
	private static final String CODE =
		"HSP1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

	@Test
	public void encryptsPairingCodeOnlyForParticipantKey()
	{
		SecureRandom random = new SecureRandom();
		KeyPair participant = DiscordPairingCipher.generateKeyPair(random);
		String encodedPublicKey = DiscordPairingCipher.encodePublicKey(
			participant.getPublic()
		);
		String encrypted = DiscordPairingCipher.encrypt(
			CODE,
			DiscordPairingCipher.decodePublicKey(encodedPublicKey),
			random
		);

		assertEquals(342, encrypted.length());
		assertFalse(encrypted.contains(CODE));
		assertEquals(CODE, DiscordPairingCipher.decrypt(
			encrypted,
			participant.getPrivate()
		));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsWrongParticipantKey()
	{
		SecureRandom random = new SecureRandom();
		KeyPair participant = DiscordPairingCipher.generateKeyPair(random);
		KeyPair other = DiscordPairingCipher.generateKeyPair(random);
		String encrypted = DiscordPairingCipher.encrypt(
			CODE,
			participant.getPublic(),
			random
		);

		DiscordPairingCipher.decrypt(encrypted, other.getPrivate());
	}
}
