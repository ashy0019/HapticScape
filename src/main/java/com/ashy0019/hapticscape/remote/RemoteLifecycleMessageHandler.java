package com.ashy0019.hapticscape.remote;

/** Handles protocol messages that directly change the enclosing session lifecycle. */
interface RemoteLifecycleMessageHandler
{
	void handleHello(String payload);

	void handleSettingsSeedRequest();

	void handlePeerEmergencyPause();

	void handlePeerResume();

	void handlePeerEnd();

	void handleHeartbeat();

	void handleHeartbeatAcknowledgement();

	void handleUnauthorizedEnd(String reason);

	void handleUnauthorizedEndAcknowledgement(String eventId);

	void publishStatus(String message);
}
