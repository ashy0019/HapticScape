package com.ashy0019.hapticscape.device;

import java.net.URI;
import java.util.List;

interface IntifaceGateway
{
	interface Listener
	{
		void onDeviceListChanged();

		void onError(String message);
	}

	void setListener(Listener listener);

	void connect(URI serverUri) throws IntifaceException, InterruptedException;

	void startScanning() throws IntifaceException, InterruptedException;

	List<DeviceInfo> getDevices();

	int vibrate(double intensity);

	void stopAll() throws IntifaceException, InterruptedException;

	boolean isConnected();

	void disconnect();
}
