package com.ashy0019.hapticscape.remote;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RemotePairingCodeTest
{
	@Test
	public void generatedCodeRoundTripsWithoutChangingDerivedValues()
	{
		RemotePairingCode original = RemotePairingCode.generate();
		RemotePairingCode restored = RemotePairingCode.parse(original.encode());

		assertTrue(original.encode().startsWith("HSP1."));
		assertEquals(48, original.encode().length());
		assertEquals(original.encode(), restored.encode());
		assertEquals(original.locator(), restored.locator());
		assertEquals(original.redeemProof(), restored.redeemProof());
		assertEquals(original.cancelProof(), restored.cancelProof());
	}

	@Test
	public void codesAreRandomAndProofsAreDomainSeparated()
	{
		RemotePairingCode first = RemotePairingCode.generate();
		RemotePairingCode second = RemotePairingCode.generate();

		assertNotEquals(first.encode(), second.encode());
		assertNotEquals(first.locator(), second.locator());
		assertNotEquals(first.redeemProof(), first.cancelProof());
		assertEquals(12, first.locator().length());
		assertEquals(43, first.redeemProof().length());
	}

	@Test
	public void pairingEnvelopeContainsNoInvitationPlaintext()
	{
		RemotePairingCode code = RemotePairingCode.generate();
		RemoteInvitation invitation = new RemoteInvitation(
			"wss://relay.example/relay",
			"roomIdentifier",
			"abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
		);
		RemoteCrypto crypto = new RemoteCrypto(code.encryptionKey());
		String envelope = crypto.encrypt(invitation.encode());

		assertFalse(envelope.contains("relay.example"));
		assertFalse(envelope.contains("roomIdentifier"));
		assertEquals(invitation.encode(), crypto.decrypt(envelope));
	}

	@Test(expected = IllegalArgumentException.class)
	public void malformedCodeIsRejected()
	{
		RemotePairingCode.parse("HSP1.not-a-valid-secret");
	}

	@Test
	public void pairingEndpointUsesRelayOrigin()
	{
		assertEquals(
			"https://relay.example/pairing/locator",
			RemotePairingService.pairingEndpoint(
				"wss://relay.example/relay?ignored=true",
				"locator"
			)
		);
		assertEquals(
			"http://localhost:8787/pairing/locator",
			RemotePairingService.pairingEndpoint(
				"ws://localhost:8787/relay",
				"locator"
			)
		);
	}

	@Test
	public void discordEndpointsUseRelayOriginAndRequiredTransport()
	{
		assertEquals(
			"https://relay.example/discord/link/code",
			RemotePairingService.serviceEndpoint(
				"wss://relay.example/relay?ignored=true",
				"/discord/link/code"
			)
		);
		assertEquals(
			"wss://relay.example/discord/device?user=123",
			RemotePairingService.webSocketEndpoint(
				"wss://relay.example/relay",
				"/discord/device",
				"user=123"
			)
		);
		assertEquals(
			"ws://localhost:8787/discord/device?user=123",
			RemotePairingService.webSocketEndpoint(
				"ws://localhost:8787/relay",
				"/discord/device",
				"user=123"
			)
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void insecureRemotePairingEndpointIsRejected()
	{
		RemotePairingService.pairingEndpoint("ws://relay.example/relay", "locator");
	}

	@Test(expected = IllegalArgumentException.class)
	public void insecureRemoteDiscordSocketIsRejected()
	{
		RemotePairingService.webSocketEndpoint(
			"ws://relay.example/relay",
			"/discord/device",
			"user=123"
		);
	}
}
