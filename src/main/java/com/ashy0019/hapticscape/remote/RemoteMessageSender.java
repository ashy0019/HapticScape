package com.ashy0019.hapticscape.remote;

/** Sends one authenticated application message through the active session. */
@FunctionalInterface
interface RemoteMessageSender
{
	boolean send(RemoteMessageType type, long version, String payload);
}
