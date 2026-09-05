package com.ashy0019.hapticscape.remote;

/** Handles protocol messages that directly change the enclosing session lifecycle. */
interface RemoteLifecycleMessageHandler
{
	void handleHello();

	void handleSettingsSeedRequest();

	void handlePeerEmergencyPause();

	void handlePeerResume();

	void handlePeerEnd();

	void publishStatus(String message);
}
