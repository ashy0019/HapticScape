package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sends participant-owned activity facts and validates them on the controller. */
final class RemoteActivityCoordinator
{
	private static final Logger LOG = Logger.getLogger(RemoteActivityCoordinator.class.getName());

	private final Gson gson;
	private final RemoteMessageSender sender;
	private final Consumer<RemoteActivityEvent> publisher;

	RemoteActivityCoordinator(
		Gson gson,
		RemoteMessageSender sender,
		Consumer<RemoteActivityEvent> publisher)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.sender = Objects.requireNonNull(sender, "sender");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
	}

	boolean publish(
		RemoteRole role,
		RemoteSessionState state,
		RemotePermissions localPermissions,
		RemoteActivityEvent event)
	{
		Objects.requireNonNull(event, "event").validate();
		if (role != RemoteRole.PARTICIPANT
			|| state != RemoteSessionState.ACTIVE
			|| !localPermissions.isActivitySharingAllowed())
		{
			return false;
		}
		return sender.send(RemoteMessageType.ACTIVITY, 0, gson.toJson(event));
	}

	void handle(RemoteRole role, RemoteSessionState state, RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER
			|| (state != RemoteSessionState.ACTIVE
				&& state != RemoteSessionState.PEER_EMERGENCY_PAUSED))
		{
			return;
		}
		try
		{
			RemoteActivityEvent event = gson.fromJson(message.getPayload(), RemoteActivityEvent.class);
			if (event == null)
			{
				return;
			}
			event.validate();
			publisher.accept(event);
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored invalid remote activity event", e);
		}
	}
}
