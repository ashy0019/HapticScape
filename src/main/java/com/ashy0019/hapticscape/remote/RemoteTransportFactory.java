package com.ashy0019.hapticscape.remote;

@FunctionalInterface
interface RemoteTransportFactory
{
	RemoteTransport create(RemoteTransport.Listener listener);
}
