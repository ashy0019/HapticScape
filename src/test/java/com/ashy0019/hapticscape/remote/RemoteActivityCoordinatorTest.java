package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteActivityCoordinatorTest
{
	@Test
	public void participantPermissionGatesActivityBeforeTransport()
	{
		Gson gson = new Gson();
		List<RemoteProtocolMessage> sent = new ArrayList<>();
		RemoteActivityCoordinator coordinator = new RemoteActivityCoordinator(
			gson,
			(type, version, payload) ->
			{
				sent.add(new RemoteProtocolMessage(type, version, payload));
				return true;
			},
			event -> { }
		);
		RemoteActivityEvent event = new RemoteActivityEvent(
			RemoteActivityType.XP_GAIN,
			"Fishing",
			"+42 XP",
			1_000
		);

		assertFalse(coordinator.publish(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.ACTIVE,
			RemotePermissions.defaults(),
			event
		));
		assertTrue(sent.isEmpty());

		RemotePermissions allowed = RemotePermissions.defaults()
			.withActivitySharingAllowed(true);
		assertTrue(coordinator.publish(
			RemoteRole.PARTICIPANT,
			RemoteSessionState.ACTIVE,
			allowed,
			event
		));
		assertEquals(1, sent.size());
		assertEquals(RemoteMessageType.ACTIVITY, sent.get(0).getType());
	}

	@Test
	public void controllerReceivesOnlyValidatedActivityMessages()
	{
		Gson gson = new Gson();
		List<RemoteActivityEvent> received = new ArrayList<>();
		RemoteActivityCoordinator coordinator = new RemoteActivityCoordinator(
			gson,
			(type, version, payload) -> true,
			received::add
		);
		RemoteActivityEvent event = new RemoteActivityEvent(
			RemoteActivityType.PLAYER_DEATH,
			"Player",
			"Died",
			2_000
		);
		RemoteProtocolMessage message = new RemoteProtocolMessage(
			RemoteMessageType.ACTIVITY,
			0,
			gson.toJson(event)
		);

		coordinator.handle(RemoteRole.PARTICIPANT, RemoteSessionState.ACTIVE, message);
		assertTrue(received.isEmpty());
		coordinator.handle(RemoteRole.CONTROLLER, RemoteSessionState.ACTIVE, message);
		assertEquals(1, received.size());
		assertEquals(RemoteActivityType.PLAYER_DEATH, received.get(0).getType());

		RemoteProtocolMessage invalid = new RemoteProtocolMessage(
			RemoteMessageType.ACTIVITY,
			0,
			"{\"type\":\"XP_GAIN\",\"label\":\"\",\"detail\":\"x\",\"timestampMillis\":1}"
		);
		coordinator.handle(RemoteRole.CONTROLLER, RemoteSessionState.ACTIVE, invalid);
		assertEquals(1, received.size());
	}
}
