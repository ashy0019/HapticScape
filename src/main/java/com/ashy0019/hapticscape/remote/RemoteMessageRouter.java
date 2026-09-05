package com.ashy0019.hapticscape.remote;

import java.util.Objects;

/** Routes authenticated protocol messages to their domain coordinator. */
final class RemoteMessageRouter
{
	private final RemoteSettingsCoordinator settings;
	private final RemotePermissionsCoordinator permissions;
	private final RemoteLockCoordinator locks;
	private final RemoteActionCoordinator actions;
	private final RemoteLifecycleMessageHandler lifecycle;

	RemoteMessageRouter(
		RemoteSettingsCoordinator settings,
		RemotePermissionsCoordinator permissions,
		RemoteLockCoordinator locks,
		RemoteActionCoordinator actions,
		RemoteLifecycleMessageHandler lifecycle)
	{
		this.settings = Objects.requireNonNull(settings, "settings");
		this.permissions = Objects.requireNonNull(permissions, "permissions");
		this.locks = Objects.requireNonNull(locks, "locks");
		this.actions = Objects.requireNonNull(actions, "actions");
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
	}

	void route(
		RemoteRole role,
		RemoteSessionState state,
		RemoteProtocolMessage message)
	{
		switch (message.getType())
		{
			case HELLO:
				lifecycle.handleHello();
				break;
			case SETTINGS_SEED_REQUEST:
				lifecycle.handleSettingsSeedRequest();
				break;
			case PERMISSIONS:
				boolean accepted = permissions.handle(role, message);
				if (accepted
					&& role == RemoteRole.CONTROLLER
					&& !permissions.getPeer().isSettingsAllowed())
				{
					lifecycle.publishStatus("Participant disabled remote settings");
				}
				break;
			case SETTINGS_SEED:
				settings.handleSeed(role, state, permissions.getPeer(), message);
				break;
			case SETTINGS_SEED_ACK:
				settings.handleSeedAcknowledgement(role, state, permissions.getLocal());
				break;
			case SETTINGS:
				settings.handleSettings(role, state, permissions.getLocal(), message);
				break;
			case SETTINGS_ACK:
				settings.handleAcknowledgement(role, message);
				break;
			case SETTINGS_REJECTED:
				settings.handleRejected(role, state);
				break;
			case REMOTE_ACTION:
				actions.handleAction(role, state, permissions.getLocal(), message);
				break;
			case REMOTE_ACTION_ACK:
				actions.handleAcknowledgement(role, message);
				break;
			case REMOTE_LIVE_HAPTIC:
				actions.handleLive(role, state, permissions.getLocal(), message);
				break;
			case LOCK_PROPOSAL:
			case LOCK_ACCEPTED:
			case LOCK_DECLINED:
			case LOCK_CANCEL_REQUEST:
			case LOCK_CANCELLED:
				locks.handle(role, message);
				break;
			case EMERGENCY_PAUSED:
				lifecycle.handlePeerEmergencyPause();
				break;
			case SESSION_RESUMED:
				lifecycle.handlePeerResume();
				break;
			case SESSION_END:
				lifecycle.handlePeerEnd();
				break;
			default:
				break;
		}
	}
}
