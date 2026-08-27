package com.ashy0019.hapticscape.device;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntifaceWebSocketGatewayTest
{
	private FakeServer server;
	private IntifaceWebSocketGateway gateway;

	@Before
	public void setUp()
	{
		server = new FakeServer();
		gateway = new IntifaceWebSocketGateway(server::open, new Gson());
	}

	@After
	public void tearDown()
	{
		gateway.disconnect();
	}

	@Test
	public void connectsWithVersionThreeHandshakeAndLoadsDevices() throws Exception
	{
		gateway.connect(new URI("ws://localhost:12345"));

		assertTrue(gateway.isConnected());
		assertEquals("localhost", server.request.url().host());
		assertEquals(12345, server.request.url().port());
		assertEquals("RequestServerInfo", server.receivedTypes.get(0));
		assertEquals("RequestDeviceList", server.receivedTypes.get(1));
		assertEquals("HapticScape", server.handshake.get("ClientName").getAsString());
		assertEquals(3, server.handshake.get("MessageVersion").getAsInt());

		List<DeviceInfo> devices = gateway.getDevices();
		assertEquals(1, devices.size());
		assertEquals("Switch Pro Controller", devices.get(0).getName());
		assertTrue(devices.get(0).isVibrationSupported());

		gateway.startScanning();
		assertTrue(server.receivedTypes.contains("StartScanning"));
	}

	@Test
	public void sendsOnlyVibrationScalarFeaturesAndStopsAllDevices() throws Exception
	{
		gateway.connect(new URI("ws://localhost:12345"));

		assertEquals(1, gateway.vibrate(0.47));
		JsonArray scalars = server.lastScalarCommand.getAsJsonArray("Scalars");
		assertEquals(1, scalars.size());
		assertEquals(0, scalars.get(0).getAsJsonObject().get("Index").getAsInt());
		assertEquals(0.47, scalars.get(0).getAsJsonObject().get("Scalar").getAsDouble(), 0.0001);
		assertEquals("Vibrate", scalars.get(0).getAsJsonObject().get("ActuatorType").getAsString());

		gateway.stopAll();
		assertEquals(1, server.stopAllCount);
	}

	@Test
	public void tracksDeviceAddedAndRemovedEvents() throws Exception
	{
		AtomicInteger changes = new AtomicInteger();
		gateway.setListener(new IntifaceGateway.Listener()
		{
			@Override
			public void onDeviceListChanged()
			{
				changes.incrementAndGet();
			}

			@Override
			public void onError(String message)
			{
			}
		});
		gateway.connect(new URI("ws://localhost:12345"));

		server.emitDeviceAdded(8, "Second controller");
		assertEquals(2, gateway.getDevices().size());
		assertEquals(1, changes.get());

		server.emitDeviceRemoved(8);
		assertEquals(1, gateway.getDevices().size());
		assertEquals(2, changes.get());
	}

	@Test
	public void turnsProtocolErrorsIntoCheckedFailures() throws Exception
	{
		gateway.connect(new URI("ws://localhost:12345"));
		server.rejectScanning = true;

		try
		{
			gateway.startScanning();
			fail("Expected IntifaceException");
		}
		catch (IntifaceException e)
		{
			assertEquals("Scanning disabled by test server", e.getMessage());
		}
	}

	@Test
	public void reportsRemoteConnectionFailure() throws Exception
	{
		List<String> errors = new ArrayList<>();
		gateway.setListener(new IntifaceGateway.Listener()
		{
			@Override
			public void onDeviceListChanged()
			{
			}

			@Override
			public void onError(String message)
			{
				errors.add(message);
			}
		});
		gateway.connect(new URI("ws://localhost:12345"));

		server.failConnection(new IOException("server disappeared"));

		assertFalse(gateway.isConnected());
		assertEquals(1, errors.size());
		assertTrue(errors.get(0).contains("server disappeared"));
	}

	private static final class FakeServer
	{
		private final Gson gson = new Gson();
		private final List<String> receivedTypes = new ArrayList<>();
		private Request request;
		private WebSocketListener listener;
		private FakeWebSocket socket;
		private JsonObject handshake;
		private JsonObject lastScalarCommand;
		private int stopAllCount;
		private boolean rejectScanning;

		private WebSocket open(Request request, WebSocketListener listener)
		{
			this.request = request;
			this.listener = listener;
			this.socket = new FakeWebSocket(request, this);
			listener.onOpen(socket, null);
			return socket;
		}

		private boolean receive(String text)
		{
			JsonObject envelope = gson.fromJson(text, JsonElement.class)
				.getAsJsonArray()
				.get(0)
				.getAsJsonObject();
			String type = envelope.entrySet().iterator().next().getKey();
			JsonObject body = envelope.getAsJsonObject(type);
			long id = body.get("Id").getAsLong();
			receivedTypes.add(type);

			switch (type)
			{
				case "RequestServerInfo":
					handshake = body.deepCopy();
					JsonObject serverInfo = new JsonObject();
					serverInfo.addProperty("ServerName", "Fake Intiface");
					serverInfo.addProperty("MessageVersion", 3);
					serverInfo.addProperty("MaxPingTime", 0);
					respond("ServerInfo", id, serverInfo);
					break;
				case "RequestDeviceList":
					JsonObject deviceList = new JsonObject();
					JsonArray devices = new JsonArray();
					devices.add(device(4, "Switch Pro Controller"));
					deviceList.add("Devices", devices);
					respond("DeviceList", id, deviceList);
					break;
				case "StartScanning":
					if (rejectScanning)
					{
						JsonObject error = new JsonObject();
						error.addProperty("ErrorMessage", "Scanning disabled by test server");
						error.addProperty("ErrorCode", 3);
						respond("Error", id, error);
					}
					else
					{
						respond("Ok", id, new JsonObject());
					}
					break;
				case "ScalarCmd":
					lastScalarCommand = body.deepCopy();
					respond("Ok", id, new JsonObject());
					break;
				case "StopAllDevices":
					stopAllCount++;
					respond("Ok", id, new JsonObject());
					break;
				case "Ping":
					respond("Ok", id, new JsonObject());
					break;
				default:
					throw new AssertionError("Unexpected message type " + type);
			}
			return true;
		}

		private void emitDeviceAdded(long index, String name)
		{
			respond("DeviceAdded", 0, device(index, name));
		}

		private void emitDeviceRemoved(long index)
		{
			JsonObject removed = new JsonObject();
			removed.addProperty("DeviceIndex", index);
			respond("DeviceRemoved", 0, removed);
		}

		private void failConnection(Throwable error)
		{
			listener.onFailure(socket, error, null);
		}

		private void respond(String type, long id, JsonObject body)
		{
			body.addProperty("Id", id);
			JsonObject message = new JsonObject();
			message.add(type, body);
			JsonArray messages = new JsonArray();
			messages.add(message);
			listener.onMessage(socket, gson.toJson(messages));
		}

		private static JsonObject device(long index, String name)
		{
			JsonObject device = new JsonObject();
			device.addProperty("DeviceIndex", index);
			device.addProperty("DeviceName", name);
			device.addProperty("DeviceDisplayName", name);

			JsonArray scalars = new JsonArray();
			JsonObject vibrate = new JsonObject();
			vibrate.addProperty("ActuatorType", "Vibrate");
			scalars.add(vibrate);
			JsonObject rotate = new JsonObject();
			rotate.addProperty("ActuatorType", "Rotate");
			scalars.add(rotate);

			JsonObject deviceMessages = new JsonObject();
			deviceMessages.add("ScalarCmd", scalars);
			device.add("DeviceMessages", deviceMessages);
			return device;
		}
	}

	private static final class FakeWebSocket implements WebSocket
	{
		private final Request request;
		private final FakeServer server;
		private boolean closed;

		private FakeWebSocket(Request request, FakeServer server)
		{
			this.request = request;
			this.server = server;
		}

		@Override
		public Request request()
		{
			return request;
		}

		@Override
		public long queueSize()
		{
			return 0;
		}

		@Override
		public boolean send(String text)
		{
			return !closed && server.receive(text);
		}

		@Override
		public boolean send(ByteString bytes)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean close(int code, String reason)
		{
			if (closed)
			{
				return false;
			}
			closed = true;
			server.listener.onClosed(this, code, reason);
			return true;
		}

		@Override
		public void cancel()
		{
			closed = true;
		}
	}
}
