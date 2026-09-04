package com.ashy0019.hapticscape.remote;

/**
 * Small transport boundary that keeps session behavior independent from the
 * WebSocket implementation and makes two-peer protocol tests deterministic.
 */
interface RemoteTransport extends AutoCloseable
{
	interface Listener
	{
		void onOpen();

		void onMessage(String message);

		void onClosed(String reason);

		void onFailure(String message, Throwable error);
	}

	void connect(String relayUrl, String roomId, RemoteRole role);

	boolean send(String message);

	boolean isOpen();

	@Override
	void close();
}
