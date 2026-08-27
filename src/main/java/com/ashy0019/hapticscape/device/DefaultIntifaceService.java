package com.ashy0019.hapticscape.device;

import com.google.gson.Gson;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public final class DefaultIntifaceService implements IntifaceService
{
	private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(10);
	private static final long CONNECTION_CHECK_SECONDS = 1;

	private final ScheduledExecutorService executor;
	private final ExecutorService connectExecutor;
	private final ExecutorService cleanupExecutor;
	private final IntifaceGatewayFactory gatewayFactory;
	private final long connectionTimeoutMillis;
	private final AtomicBoolean closing = new AtomicBoolean();

	private volatile ConnectionSnapshot snapshot = ConnectionSnapshot.disconnected();
	private volatile Consumer<ConnectionSnapshot> connectionListener = ignored -> { };

	// The fields below are owned exclusively by the service executor.
	private IntifaceGateway gateway;
	private Future<?> connectAttempt;
	private ScheduledFuture<?> connectTimeout;
	private ScheduledFuture<?> pendingStop;
	private ScheduledFuture<?> connectionMonitor;
	private long connectionGeneration;

	public DefaultIntifaceService(OkHttpClient httpClient, Gson gson)
	{
		this(
			() -> new IntifaceWebSocketGateway(httpClient, gson),
			DEFAULT_CONNECTION_TIMEOUT
		);
	}

	DefaultIntifaceService(IntifaceGatewayFactory gatewayFactory)
	{
		this(gatewayFactory, DEFAULT_CONNECTION_TIMEOUT);
	}

	DefaultIntifaceService(IntifaceGatewayFactory gatewayFactory, Duration connectionTimeout)
	{
		this.gatewayFactory = Objects.requireNonNull(gatewayFactory, "gatewayFactory");
		this.connectionTimeoutMillis = Math.max(
			1,
			Objects.requireNonNull(connectionTimeout, "connectionTimeout").toMillis()
		);
		this.executor = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-intiface");
			thread.setDaemon(true);
			return thread;
		});
		this.connectExecutor = Executors.newSingleThreadExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-connect");
			thread.setDaemon(true);
			return thread;
		});
		this.cleanupExecutor = Executors.newSingleThreadExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-cleanup");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Override
	public void connect(URI serverUri)
	{
		Objects.requireNonNull(serverUri, "serverUri");
		submit(() -> beginConnect(serverUri));
	}

	@Override
	public void disconnect()
	{
		submit(() -> disconnectInternal("Disconnected"));
	}

	@Override
	public void pulse(double intensity, Duration duration)
	{
		Objects.requireNonNull(duration, "duration");
		double safeIntensity = Math.max(0.0, Math.min(1.0, intensity));
		long durationMillis = Math.max(1, duration.toMillis());
		submit(() -> pulseInternal(safeIntensity, durationMillis));
	}

	@Override
	public void stopAll()
	{
		submit(() -> stopAllInternal(true));
	}

	@Override
	public ConnectionSnapshot getSnapshot()
	{
		return snapshot;
	}

	@Override
	public void setConnectionListener(Consumer<ConnectionSnapshot> listener)
	{
		connectionListener = Objects.requireNonNull(listener, "listener");
		connectionListener.accept(snapshot);
	}

	@Override
	public void close()
	{
		if (!closing.compareAndSet(false, true))
		{
			return;
		}

		try
		{
			executor.execute(this::closeInternal);
		}
		catch (RejectedExecutionException e)
		{
			connectExecutor.shutdownNow();
			cleanupExecutor.shutdownNow();
			executor.shutdownNow();
		}
	}

	private void beginConnect(URI serverUri)
	{
		if (closing.get())
		{
			return;
		}

		cleanupGateway();
		publish(ConnectionState.CONNECTING, "Connecting to " + serverUri, Collections.emptyList());

		IntifaceGateway candidate = gatewayFactory.create();
		candidate.setListener(new IntifaceGateway.Listener()
		{
			@Override
			public void onDeviceListChanged()
			{
				submitCallback(() -> refreshDevices(candidate));
			}

			@Override
			public void onError(String message)
			{
				submitCallback(() -> handleGatewayError(candidate, message));
			}
		});
		gateway = candidate;
		long generation = ++connectionGeneration;

		try
		{
			connectAttempt = connectExecutor.submit(
				() -> connectBlocking(candidate, serverUri, generation)
			);
			connectTimeout = executor.schedule(
				() -> runGuarded(() -> handleConnectionTimeout(candidate, generation)),
				connectionTimeoutMillis,
				TimeUnit.MILLISECONDS
			);
		}
		catch (RejectedExecutionException e)
		{
			cleanupGateway();
			publish(ConnectionState.DISCONNECTED, "Connection worker is unavailable", Collections.emptyList());
		}
	}

	private void connectBlocking(IntifaceGateway candidate, URI serverUri, long generation)
	{
		try
		{
			candidate.connect(serverUri);
			if (closing.get())
			{
				disconnectGatewayQuietly(candidate);
				return;
			}
			submitCallback(() -> finishConnection(candidate, generation));
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			if (!closing.get())
			{
				submitCallback(() -> handleConnectionInterrupted(candidate, generation));
			}
		}
		catch (IntifaceException e)
		{
			if (!closing.get())
			{
				submitCallback(() -> handleConnectionFailure(candidate, generation, e));
			}
		}
	}

	private void finishConnection(IntifaceGateway candidate, long generation)
	{
		if (!isCurrentConnection(candidate, generation))
		{
			disconnectGatewayAsync(candidate);
			return;
		}

		cancelConnectTimeout();
		connectAttempt = null;
		try
		{
			candidate.startScanning();
			publishConnected(candidate.getDevices());
			connectionMonitor = executor.scheduleAtFixedRate(
				() -> runGuarded(this::checkConnection),
				CONNECTION_CHECK_SECONDS,
				CONNECTION_CHECK_SECONDS,
				TimeUnit.SECONDS
			);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			cleanupGateway();
			publish(ConnectionState.DISCONNECTED, "Connection interrupted", Collections.emptyList());
		}
		catch (IntifaceException e)
		{
			log.warn("Unable to start Intiface device scan", e);
			cleanupGateway();
			publish(ConnectionState.DISCONNECTED, conciseError("Connection failed", e), Collections.emptyList());
		}
	}

	private void handleConnectionInterrupted(IntifaceGateway candidate, long generation)
	{
		if (!isCurrentConnection(candidate, generation))
		{
			return;
		}
		cleanupGateway();
		publish(ConnectionState.DISCONNECTED, "Connection cancelled", Collections.emptyList());
	}

	private void handleConnectionFailure(
		IntifaceGateway candidate,
		long generation,
		IntifaceException error)
	{
		if (!isCurrentConnection(candidate, generation))
		{
			return;
		}
		log.warn("Unable to connect to Intiface", error);
		cleanupGateway();
		publish(ConnectionState.DISCONNECTED, conciseError("Connection failed", error), Collections.emptyList());
	}

	private void handleConnectionTimeout(IntifaceGateway candidate, long generation)
	{
		if (!isCurrentConnection(candidate, generation))
		{
			return;
		}
		log.warn("Intiface connection attempt timed out");
		cleanupGateway();
		publish(
			ConnectionState.DISCONNECTED,
			"Connection timed out after " + connectionTimeoutDescription(),
			Collections.emptyList()
		);
	}

	private boolean isCurrentConnection(IntifaceGateway candidate, long generation)
	{
		return candidate == gateway && generation == connectionGeneration && !closing.get();
	}

	private void pulseInternal(double intensity, long durationMillis)
	{
		if (!hasLiveConnection())
		{
			return;
		}

		int submittedCommands = gateway.vibrate(intensity);
		if (submittedCommands == 0)
		{
			publish(ConnectionState.CONNECTED, "Connected — no vibration-capable devices", gateway.getDevices());
			return;
		}

		cancelPendingStop();
		pendingStop = executor.schedule(
			() -> runGuarded(() -> stopAllInternal(false)),
			durationMillis,
			TimeUnit.MILLISECONDS
		);
	}

	private void stopAllInternal(boolean userRequested)
	{
		cancelPendingStop();
		if (!hasLiveConnection())
		{
			return;
		}

		try
		{
			gateway.stopAll();
			if (userRequested)
			{
				publish(ConnectionState.CONNECTED, "All devices stopped", gateway.getDevices());
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			log.debug("Stopping devices was interrupted", e);
		}
		catch (IntifaceException e)
		{
			log.warn("Unable to stop Intiface devices", e);
			handleCommandFailure("Unable to stop devices", e);
		}
	}

	private void disconnectInternal(String message)
	{
		if (gateway == null)
		{
			publish(ConnectionState.DISCONNECTED, message, Collections.emptyList());
			return;
		}

		publish(ConnectionState.DISCONNECTING, "Disconnecting", gateway.getDevices());
		cleanupGateway();
		publish(ConnectionState.DISCONNECTED, message, Collections.emptyList());
	}

	private void refreshDevices(IntifaceGateway source)
	{
		if (source != gateway || !hasLiveConnection())
		{
			return;
		}
		publishConnected(source.getDevices());
	}

	private void handleGatewayError(IntifaceGateway source, String message)
	{
		if (source != gateway || closing.get())
		{
			return;
		}

		String safeMessage = message == null || message.trim().isEmpty() ? "Unknown Intiface error" : message;
		if (!source.isConnected())
		{
			log.warn("Intiface connection lost: {}", safeMessage);
			cleanupGateway();
			publish(ConnectionState.DISCONNECTED, "Connection lost: " + safeMessage, Collections.emptyList());
		}
		else
		{
			log.warn("Intiface error: {}", safeMessage);
			publish(ConnectionState.CONNECTED, "Intiface error: " + safeMessage, source.getDevices());
		}
	}

	private void checkConnection()
	{
		if (gateway != null && !gateway.isConnected())
		{
			cleanupGateway();
			publish(ConnectionState.DISCONNECTED, "Intiface connection closed", Collections.emptyList());
		}
	}

	private boolean hasLiveConnection()
	{
		if (gateway == null || !gateway.isConnected())
		{
			if (gateway != null)
			{
				cleanupGateway();
				publish(ConnectionState.DISCONNECTED, "Intiface connection closed", Collections.emptyList());
			}
			return false;
		}
		return true;
	}

	private void handleCommandFailure(String prefix, IntifaceException error)
	{
		if (gateway == null || !gateway.isConnected())
		{
			cleanupGateway();
			publish(ConnectionState.DISCONNECTED, conciseError("Connection lost", error), Collections.emptyList());
		}
		else
		{
			publish(ConnectionState.CONNECTED, conciseError(prefix, error), gateway.getDevices());
		}
	}

	private void cleanupGateway()
	{
		cancelConnectionAttempt();
		cancelPendingStop();
		cancelConnectionMonitor();

		IntifaceGateway current = gateway;
		gateway = null;
		if (current == null)
		{
			return;
		}

		disconnectGatewayAsync(current);
	}

	private void closeInternal()
	{
		cleanupGateway();
		publish(ConnectionState.CLOSED, "Plugin stopped", Collections.emptyList());
		connectExecutor.shutdownNow();
		try
		{
			cleanupExecutor.execute(cleanupExecutor::shutdownNow);
		}
		catch (RejectedExecutionException e)
		{
			cleanupExecutor.shutdownNow();
		}
		executor.shutdownNow();
	}

	private void cancelConnectionAttempt()
	{
		connectionGeneration++;
		cancelConnectTimeout();

		Future<?> currentAttempt = connectAttempt;
		connectAttempt = null;
		if (currentAttempt != null && !currentAttempt.isDone())
		{
			currentAttempt.cancel(true);
		}
	}

	private void cancelConnectTimeout()
	{
		if (connectTimeout != null)
		{
			connectTimeout.cancel(false);
			connectTimeout = null;
		}
	}

	private void cancelPendingStop()
	{
		if (pendingStop != null)
		{
			pendingStop.cancel(false);
			pendingStop = null;
		}
	}

	private void cancelConnectionMonitor()
	{
		if (connectionMonitor != null)
		{
			connectionMonitor.cancel(false);
			connectionMonitor = null;
		}
	}

	private void publishConnected(java.util.List<DeviceInfo> devices)
	{
		String message = devices.isEmpty()
			? "Connected — scanning for devices"
			: "Connected — " + devices.size() + (devices.size() == 1 ? " device" : " devices");
		publish(ConnectionState.CONNECTED, message, devices);
	}

	private void publish(ConnectionState state, String message, java.util.List<DeviceInfo> devices)
	{
		ConnectionSnapshot next = new ConnectionSnapshot(state, message, devices);
		snapshot = next;
		connectionListener.accept(next);
	}

	private void submit(Runnable operation)
	{
		if (closing.get())
		{
			return;
		}
		try
		{
			executor.execute(() -> runGuarded(operation));
		}
		catch (RejectedExecutionException e)
		{
			log.debug("Ignoring Intiface operation after shutdown", e);
		}
	}

	private void runGuarded(Runnable operation)
	{
		try
		{
			operation.run();
		}
		catch (RuntimeException e)
		{
			log.error("Unexpected Intiface service failure", e);
		}
	}

	private void disconnectGatewayQuietly(IntifaceGateway target)
	{
		if (target.isConnected())
		{
			try
			{
				target.stopAll();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				log.debug("Device cleanup interrupted", e);
			}
			catch (IntifaceException e)
			{
				log.warn("Unable to stop devices during cleanup", e);
			}
		}

		try
		{
			target.disconnect();
		}
		catch (RuntimeException e)
		{
			log.warn("Unable to disconnect Intiface cleanly", e);
		}
	}

	private void disconnectGatewayAsync(IntifaceGateway target)
	{
		try
		{
			cleanupExecutor.execute(() -> disconnectGatewayQuietly(target));
		}
		catch (RejectedExecutionException e)
		{
			log.debug("Unable to schedule Intiface cleanup after shutdown", e);
		}
	}

	private void submitCallback(Runnable callback)
	{
		if (closing.get())
		{
			return;
		}
		try
		{
			executor.execute(callback);
		}
		catch (RejectedExecutionException e)
		{
			log.debug("Ignoring Intiface callback after shutdown", e);
		}
	}

	private static String conciseError(String prefix, IntifaceException error)
	{
		String detail = error.getMessage();
		return detail == null || detail.trim().isEmpty() ? prefix : prefix + ": " + detail;
	}

	private String connectionTimeoutDescription()
	{
		return connectionTimeoutMillis % 1_000 == 0
			? connectionTimeoutMillis / 1_000 + " seconds"
			: connectionTimeoutMillis + " ms";
	}
}
