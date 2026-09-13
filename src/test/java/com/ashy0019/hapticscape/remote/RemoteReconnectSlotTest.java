package com.ashy0019.hapticscape.remote;

import java.util.Base64;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RemoteReconnectSlotTest
{
	@Test
	public void reconnectSlotIsStableAndRoleSpecific()
	{
		String sessionKey = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(new byte[32]);

		String controller = RemoteReconnectSlot.derive(sessionKey, RemoteRole.CONTROLLER);
		String participant = RemoteReconnectSlot.derive(sessionKey, RemoteRole.PARTICIPANT);

		assertEquals(43, controller.length());
		assertEquals(controller, RemoteReconnectSlot.derive(sessionKey, RemoteRole.CONTROLLER));
		assertFalse(controller.equals(participant));
	}

	@Test
	public void reconnectSlotChangesWithSessionKey()
	{
		byte[] firstKey = new byte[32];
		byte[] secondKey = new byte[32];
		secondKey[0] = 1;

		assertFalse(
			RemoteReconnectSlot.derive(
				Base64.getUrlEncoder().withoutPadding().encodeToString(firstKey),
				RemoteRole.PARTICIPANT
			).equals(
			RemoteReconnectSlot.derive(
				Base64.getUrlEncoder().withoutPadding().encodeToString(secondKey),
				RemoteRole.PARTICIPANT
			))
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void inactiveRoleCannotClaimReconnectSlot()
	{
		RemoteReconnectSlot.derive(
			Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),
			RemoteRole.NONE
		);
	}
}
