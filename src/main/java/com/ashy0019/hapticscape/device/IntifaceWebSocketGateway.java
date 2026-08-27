package com.ashy0019.hapticscape.device;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class IntifaceWebSocketGateway implements IntifaceGateway
{
	private static final int PROTOCOL_VERSION = 3;
	private static final long MAX_MESSAGE_ID = 4_294_967_295L;
	private static final long COMMAND_TIMEOUT_MILLIS = 2_000;

	interface WebSocketConnector
	{
		WebSocket open(Request request, WebSocketListener listener);
	}

	private static final Listener NOOP_LISTENER = new Listener()
	{
		@Override
		public void onDeviceListChanged()
		{
		}

		@Override
		public void onError(String message)
		{
		}
	};

	private final WebSocketConnector connector;
	private final Gson gson;
	private final ScheduledExecutorService scheduler;
	private final ConcurrentHashMap<Long, CompletableFuture<ProtocolMessage>> pendingMessages =
		new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, ProtocolDevice> devices = new ConcurrentHashMap<>();
	private final AtomicLong nextMessageId = new AtomicLong(1);
	private final AtomicBoolean connected = new AtomicBoolean();
	private final AtomicBoolean manualClose = new AtomicBoolean();
	private final AtomicBoolean failureSignaled = new AtomicBoolean();
	private final CompletableFuture<Void> socketOpened = new CompletableFuture<>();

	private volatile Listener listener = NOOP_LISTENER;
	private volatile WebSocket webSocket;
	private volatile ScheduledFuture<?> pingTask;

	IntifaceWebSocketGateway(OkHttpClient httpClient, Gson gson)
	{
		this(Objects.requireNonNull(httpClient, "httpClient")::newWebSocket, gson);
	}

	IntifaceWebSocketGateway(WebSocketConnector connector, Gson gson)
	{
		this.connector = Objects.requireNonNull(connector, "connector");
		this.gson = Objects.requireNonNull(gson, "gson");
		this.scheduler = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-protocol");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Override
	public void setListener(Listener listener)
	{
		this.listener = Objects.requireNonNull(listener, "listener");
	}

	@Override
	public void connect(URI serverUri) throws IntifaceException, InterruptedException
	{
		Objects.requireNonNull(serverUri, "serverUri");
		if (webSocket != null)
		{
			throw new IntifaceException("Intiface connection is already open");
		}

		manualClose.set(false);
		failureSignaled.set(false);
		devices.clear();
		nextMessageId.set(1);

		try
		{
			Request request = new Request.Builder().url(serverUri.toString()).build();
			webSocket = connector.open(request, new SocketListener());
			await(socketOpened, "Unable to open Intiface WebSocket");

			JsonObject handshake = new JsonObject();
			handshake.addProperty("ClientName", "HapticScape");
			handshake.addProperty("MessageVersion", PROTOCOL_VERSION);
			ProtocolMessage serverInfo = sendAndAwait("RequestServerInfo", handshake);
			requireResponse(serverInfo, "ServerInfo", "Intiface handshake failed");

			int negotiatedVersion = getRequiredInt(serverInfo.body, "MessageVersion");
			if (negotiatedVersion != PROTOCOL_VERSION)
			{
				throw new IntifaceException(
					"Intiface negotiated unsupported protocol version " + negotiatedVersion
				);
			}

			ProtocolMessage deviceList = sendAndAwait("RequestDeviceList", new JsonObject());
			requireResponse(deviceList, "DeviceList", "Unable to request Intiface devices");
			if (failureSignaled.get())
			{
				throw new IntifaceException("Intiface connection closed during handshake");
			}
			connected.set(true);
			if (failureSignaled.get())
			{
				connected.set(false);
				throw new IntifaceException("Intiface connection closed during handshake");
			}
			startProtocolPing(getOptionalLong(serverInfo.body, "MaxPingTime", 0));
		}
		catch (InterruptedException e)
		{
			disconnect();
			throw e;
		}
		catch (IntifaceException e)
		{
			disconnect();
			throw e;
		}
		catch (RuntimeException e)
		{
			disconnect();
			throw new IntifaceException("Unable to connect to Intiface", e);
		}
	}

	@Override
	public void startScanning() throws IntifaceException, InterruptedException
	{
		requireConnected();
		ProtocolMessage response = sendAndAwait("StartScanning", new JsonObject());
		requireResponse(response, "Ok", "Unable to start device scanning");
	}

	@Override
	public List<DeviceInfo> getDevices()
	{
		List<DeviceInfo> result = new ArrayList<>();
		for (ProtocolDevice device : devices.values())
		{
			result.add(new DeviceInfo(
				device.index,
				device.name,
				!device.vibrationIndices.isEmpty()
			));
		}
		result.sort(Comparator.comparing(DeviceInfo::getName));
		return result;
	}

	@Override
	public int vibrate(double intensity)
	{
		if (!isConnected())
		{
			return 0;
		}

		double safeIntensity = Math.max(0.0, Math.min(1.0, intensity));
		int submittedCommands = 0;
		for (ProtocolDevice device : devices.values())
		{
			if (device.vibrationIndices.isEmpty())
			{
				continue;
			}

			JsonArray scalars = new JsonArray();
			for (int index : device.vibrationIndices)
			{
				JsonObject scalar = new JsonObject();
				scalar.addProperty("Index", index);
				scalar.addProperty("Scalar", safeIntensity);
				scalar.addProperty("ActuatorType", "Vibrate");
				scalars.add(scalar);
			}

			JsonObject command = new JsonObject();
			command.addProperty("DeviceIndex", device.index);
			command.add("Scalars", scalars);
			try
			{
				sendMessage("ScalarCmd", command).whenComplete((response, error) ->
				{
					if (error != null && !manualClose.get())
					{
						listener.onError(
							"Unable to vibrate " + device.name + ": " + conciseError(error)
						);
					}
				});
				submittedCommands++;
			}
			catch (IntifaceException e)
			{
				listener.onError("Unable to vibrate " + device.name + ": " + e.getMessage());
			}
		}
		return submittedCommands;
	}

	@Override
	public void stopAll() throws IntifaceException, InterruptedException
	{
		requireConnected();
		ProtocolMessage response = sendAndAwait("StopAllDevices", new JsonObject());
		requireResponse(response, "Ok", "Unable to stop devices");
	}

	@Override
	public boolean isConnected()
	{
		return connected.get() && webSocket != null;
	}

	@Override
	public void disconnect()
	{
		manualClose.set(true);
		connected.set(false);
		cancelPing();
		failPending(new IntifaceException("Intiface connection closed"));
		devices.clear();

		WebSocket current = webSocket;
		webSocket = null;
		if (current != null && !current.close(1000, "HapticScape disconnected"))
		{
			current.cancel();
		}
		scheduler.shutdownNow();
	}

	private ProtocolMessage sendAndAwait(String messageType, JsonObject body)
		throws IntifaceException, InterruptedException
	{
		return await(sendMessage(messageType, body), "No response to " + messageType);
	}

	private CompletableFuture<ProtocolMessage> sendMessage(String messageType, JsonObject body)
		throws IntifaceException
	{
		WebSocket current = webSocket;
		if (current == null)
		{
			throw new IntifaceException("Intiface WebSocket is not open");
		}

		long id = nextId();
		body.addProperty("Id", id);

		JsonObject message = new JsonObject();
		message.add(messageType, body);
		JsonArray messages = new JsonArray();
		messages.add(message);

		CompletableFuture<ProtocolMessage> response = new CompletableFuture<>();
		pendingMessages.put(id, response);
		try
		{
			ScheduledFuture<?> timeout = scheduler.schedule(() ->
			{
				CompletableFuture<ProtocolMessage> pending = pendingMessages.remove(id);
				if (pending != null)
				{
					pending.completeExceptionally(
						new IntifaceException(messageType + " timed out")
					);
				}
			}, COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			response.whenComplete((ignored, error) -> timeout.cancel(false));
		}
		catch (RejectedExecutionException e)
		{
			pendingMessages.remove(id);
			throw new IntifaceException("Intiface connection is shutting down", e);
		}

		if (!current.send(gson.toJson(messages)))
		{
			pendingMessages.remove(id);
			response.completeExceptionally(new IntifaceException("Unable to send " + messageType));
		}
		return response;
	}

	private void handleTextMessage(String text)
	{
		try
		{
			JsonElement root = gson.fromJson(text, JsonElement.class);
			if (!root.isJsonArray())
			{
				throw new IllegalArgumentException("Expected a JSON message array");
			}

			for (JsonElement element : root.getAsJsonArray())
			{
				handleMessage(element);
			}
		}
		catch (RuntimeException e)
		{
			signalConnectionFailure("Invalid response from Intiface", e);
		}
	}

	private void handleMessage(JsonElement element)
	{
		if (!element.isJsonObject() || element.getAsJsonObject().entrySet().size() != 1)
		{
			throw new IllegalArgumentException("Invalid Intiface message envelope");
		}

		Map.Entry<String, JsonElement> entry = element.getAsJsonObject().entrySet().iterator().next();
		if (!entry.getValue().isJsonObject())
		{
			throw new IllegalArgumentException("Invalid Intiface message body");
		}

		String type = entry.getKey();
		JsonObject body = entry.getValue().getAsJsonObject();
		long id = getOptionalLong(body, "Id", 0);

		if ("DeviceList".equals(type))
		{
			replaceDevices(body);
		}
		else if ("DeviceAdded".equals(type))
		{
			ProtocolDevice device = parseDevice(body);
			devices.put(device.index, device);
			if (connected.get())
			{
				listener.onDeviceListChanged();
			}
		}
		else if ("DeviceRemoved".equals(type))
		{
			devices.remove(getRequiredLong(body, "DeviceIndex"));
			if (connected.get())
			{
				listener.onDeviceListChanged();
			}
		}
		else if ("Error".equals(type))
		{
			handleProtocolError(id, body);
			return;
		}

		if (id > 0)
		{
			CompletableFuture<ProtocolMessage> pending = pendingMessages.remove(id);
			if (pending != null)
			{
				pending.complete(new ProtocolMessage(type, body));
			}
		}
	}

	private void replaceDevices(JsonObject body)
	{
		ConcurrentHashMap<Long, ProtocolDevice> replacement = new ConcurrentHashMap<>();
		JsonElement deviceElement = body.get("Devices");
		if (deviceElement != null && deviceElement.isJsonArray())
		{
			for (JsonElement element : deviceElement.getAsJsonArray())
			{
				if (!element.isJsonObject())
				{
					throw new IllegalArgumentException("Invalid device list entry");
				}
				ProtocolDevice device = parseDevice(element.getAsJsonObject());
				replacement.put(device.index, device);
			}
		}

		devices.clear();
		devices.putAll(replacement);
		if (connected.get())
		{
			listener.onDeviceListChanged();
		}
	}

	private static ProtocolDevice parseDevice(JsonObject body)
	{
		long index = getRequiredLong(body, "DeviceIndex");
		String name = getOptionalString(body, "DeviceDisplayName");
		if (name.isEmpty())
		{
			name = getOptionalString(body, "DeviceName");
		}
		if (name.isEmpty())
		{
			name = "Device " + index;
		}

		List<Integer> vibrationIndices = new ArrayList<>();
		JsonElement deviceMessagesElement = body.get("DeviceMessages");
		if (deviceMessagesElement != null && deviceMessagesElement.isJsonObject())
		{
			JsonElement scalarElement = deviceMessagesElement.getAsJsonObject().get("ScalarCmd");
			if (scalarElement != null && scalarElement.isJsonArray())
			{
				JsonArray scalarFeatures = scalarElement.getAsJsonArray();
				for (int featureIndex = 0; featureIndex < scalarFeatures.size(); featureIndex++)
				{
					JsonElement feature = scalarFeatures.get(featureIndex);
					if (feature.isJsonObject()
						&& "Vibrate".equalsIgnoreCase(
							getOptionalString(feature.getAsJsonObject(), "ActuatorType")
						))
					{
						vibrationIndices.add(featureIndex);
					}
				}
			}
		}
		return new ProtocolDevice(index, name, vibrationIndices);
	}

	private void handleProtocolError(long id, JsonObject body)
	{
		String message = getOptionalString(body, "ErrorMessage");
		if (message.isEmpty())
		{
			message = "Unknown Intiface protocol error";
		}

		if (id > 0)
		{
			CompletableFuture<ProtocolMessage> pending = pendingMessages.remove(id);
			if (pending != null)
			{
				pending.completeExceptionally(new IntifaceException(message));
				return;
			}
		}
		listener.onError(message);
	}

	private void startProtocolPing(long maxPingTimeMillis)
	{
		if (maxPingTimeMillis <= 0)
		{
			return;
		}

		long intervalMillis = Math.max(25, maxPingTimeMillis / 2);
		pingTask = scheduler.scheduleAtFixedRate(() ->
		{
			if (!isConnected())
			{
				return;
			}
			try
			{
				sendMessage("Ping", new JsonObject()).whenComplete((response, error) ->
				{
					if (error != null && !manualClose.get())
					{
						signalConnectionFailure("Intiface ping failed", error);
					}
				});
			}
			catch (IntifaceException e)
			{
				signalConnectionFailure("Intiface ping failed", e);
			}
		}, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
	}

	private void signalConnectionFailure(String message, Throwable cause)
	{
		boolean wasConnected = connected.getAndSet(false);
		boolean firstFailure = failureSignaled.compareAndSet(false, true);
		cancelPing();
		socketOpened.completeExceptionally(cause);
		failPending(cause);

		WebSocket current = webSocket;
		if (current != null)
		{
			current.cancel();
		}
		if (wasConnected && !manualClose.get() && firstFailure)
		{
			listener.onError(message + ": " + conciseError(cause));
		}
	}

	private void cancelPing()
	{
		ScheduledFuture<?> current = pingTask;
		pingTask = null;
		if (current != null)
		{
			current.cancel(false);
		}
	}

	private void failPending(Throwable cause)
	{
		for (Map.Entry<Long, CompletableFuture<ProtocolMessage>> entry : pendingMessages.entrySet())
		{
			if (pendingMessages.remove(entry.getKey(), entry.getValue()))
			{
				entry.getValue().completeExceptionally(cause);
			}
		}
	}

	private void requireConnected() throws IntifaceException
	{
		if (!isConnected())
		{
			throw new IntifaceException("Not connected to Intiface");
		}
	}

	private long nextId()
	{
		return nextMessageId.getAndUpdate(current -> current >= MAX_MESSAGE_ID ? 1 : current + 1);
	}

	private static <T> T await(CompletableFuture<T> future, String failureMessage)
		throws IntifaceException, InterruptedException
	{
		try
		{
			return future.get();
		}
		catch (InterruptedException e)
		{
			throw e;
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause() == null ? e : e.getCause();
			if (cause instanceof IntifaceException)
			{
				throw (IntifaceException) cause;
			}
			throw new IntifaceException(failureMessage, cause);
		}
	}

	private static void requireResponse(
		ProtocolMessage response,
		String expectedType,
		String failureMessage) throws IntifaceException
	{
		if (!expectedType.equals(response.type))
		{
			throw new IntifaceException(
				failureMessage + ": expected " + expectedType + " but received " + response.type
			);
		}
	}

	private static int getRequiredInt(JsonObject object, String name)
	{
		return object.get(name).getAsInt();
	}

	private static long getRequiredLong(JsonObject object, String name)
	{
		return object.get(name).getAsLong();
	}

	private static long getOptionalLong(JsonObject object, String name, long fallback)
	{
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? fallback : value.getAsLong();
	}

	private static String getOptionalString(JsonObject object, String name)
	{
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
	}

	private static String conciseError(Throwable error)
	{
		Throwable current = error;
		while (current.getCause() != null)
		{
			current = current.getCause();
		}
		String message = current.getMessage();
		return message == null || message.trim().isEmpty()
			? current.getClass().getSimpleName()
			: message;
	}

	private final class SocketListener extends WebSocketListener
	{
		@Override
		public void onOpen(WebSocket socket, Response response)
		{
			socketOpened.complete(null);
		}

		@Override
		public void onMessage(WebSocket socket, String text)
		{
			handleTextMessage(text);
		}

		@Override
		public void onClosing(WebSocket socket, int code, String reason)
		{
			socket.close(code, reason);
		}

		@Override
		public void onClosed(WebSocket socket, int code, String reason)
		{
			boolean wasConnected = connected.getAndSet(false);
			boolean firstFailure = failureSignaled.compareAndSet(false, true);
			cancelPing();
			IntifaceException error = new IntifaceException("Intiface connection closed");
			socketOpened.completeExceptionally(error);
			failPending(error);
			if (wasConnected && !manualClose.get() && firstFailure)
			{
				String detail = reason == null || reason.trim().isEmpty()
					? "WebSocket closed"
					: reason;
				listener.onError(detail);
			}
		}

		@Override
		public void onFailure(WebSocket socket, Throwable error, Response response)
		{
			signalConnectionFailure("Intiface connection failed", error);
		}
	}

	private static final class ProtocolMessage
	{
		private final String type;
		private final JsonObject body;

		private ProtocolMessage(String type, JsonObject body)
		{
			this.type = type;
			this.body = body;
		}
	}

	private static final class ProtocolDevice
	{
		private final long index;
		private final String name;
		private final List<Integer> vibrationIndices;

		private ProtocolDevice(long index, String name, List<Integer> vibrationIndices)
		{
			this.index = index;
			this.name = name;
			this.vibrationIndices = Collections.unmodifiableList(new ArrayList<>(vibrationIndices));
		}
	}
}
