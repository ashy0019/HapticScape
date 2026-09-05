package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns participant permissions and the controller's read-only peer view. */
final class RemotePermissionsCoordinator
{
	private static final Logger LOG = Logger.getLogger(
		RemotePermissionsCoordinator.class.getName()
	);

	private final Gson gson;
	private final RemotePermissionsStore store;
	private final RemoteMessageSender sender;
	private final Consumer<RemotePermissions> publisher;
	private final Runnable livePermissionRevoked;

	private volatile RemotePermissions localPermissions;
	private volatile RemotePermissions peerPermissions = RemotePermissions.none();

	RemotePermissionsCoordinator(
		Gson gson,
		RemotePermissionsStore store,
		RemoteMessageSender sender,
		Consumer<RemotePermissions> publisher,
		Runnable livePermissionRevoked)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.store = Objects.requireNonNull(store, "store");
		this.sender = Objects.requireNonNull(sender, "sender");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
		this.livePermissionRevoked = Objects.requireNonNull(
			livePermissionRevoked,
			"livePermissionRevoked"
		);
		captureLocal(true);
	}

	RemotePermissions getLocal()
	{
		return localPermissions;
	}

	RemotePermissions getPeer()
	{
		return peerPermissions;
	}

	RemotePermissions getVisible(RemoteRole role)
	{
		return role == RemoteRole.CONTROLLER ? peerPermissions : localPermissions;
	}

	RemotePermissions updateLocal(RemoteRole role, RemotePermissions permissions)
	{
		if (role == RemoteRole.CONTROLLER)
		{
			throw new IllegalStateException("A controller cannot change participant permissions");
		}
		RemotePermissions saved = store.save(Objects.requireNonNull(permissions, "permissions"));
		saved.validate();
		localPermissions = saved;
		publisher.accept(saved);
		if (role == RemoteRole.PARTICIPANT)
		{
			send(role);
		}
		return saved;
	}

	boolean handle(RemoteRole role, RemoteProtocolMessage message)
	{
		if (role != RemoteRole.CONTROLLER)
		{
			return false;
		}
		try
		{
			RemotePermissions permissions = gson.fromJson(
				message.getPayload(),
				RemotePermissions.class
			);
			if (permissions == null)
			{
				return false;
			}
			permissions.validate();
			peerPermissions = permissions;
			if (!permissions.isLiveHapticsAllowed())
			{
				livePermissionRevoked.run();
			}
			publisher.accept(permissions);
			return true;
		}
		catch (RuntimeException e)
		{
			LOG.log(Level.FINE, "Ignored invalid remote permissions", e);
			return false;
		}
	}

	void send(RemoteRole role)
	{
		if (role == RemoteRole.PARTICIPANT)
		{
			sender.send(RemoteMessageType.PERMISSIONS, 0, gson.toJson(localPermissions));
		}
	}

	void beginSession()
	{
		captureLocal(true);
		peerPermissions = RemotePermissions.none();
	}

	void endSession()
	{
		captureLocal(false);
		peerPermissions = RemotePermissions.none();
	}

	private void captureLocal(boolean validate)
	{
		RemotePermissions captured = store.capture();
		if (validate)
		{
			captured.validate();
		}
		localPermissions = captured;
	}
}
