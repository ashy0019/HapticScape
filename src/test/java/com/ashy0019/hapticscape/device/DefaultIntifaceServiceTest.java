package com.ashy0019.hapticscape.device;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefaultIntifaceServiceTest
{
	private final FakeGateway gateway = new FakeGateway();
	private final DefaultIntifaceService service = new DefaultIntifaceService(() -> gateway);

	@After
	public void tearDown()
	{
		service.close();
	}

	@Test
	public void connectPublishesConnectedDevices() throws Exception
	{
		ConnectionSnapshot connected = connect();

		assertEquals(ConnectionState.CONNECTED, connected.getState());
		assertEquals(1, connected.getDevices().size());
		assertTrue(gateway.scanningStarted);
	}

	@Test
	public void pulseVibratesAndSchedulesStop() throws Exception
	{
		connect();
		service.pulse(0.65, Duration.ofMillis(25));

		assertEquals(0.65, gateway.vibrated.get(1, TimeUnit.SECONDS), 0.0001);
		gateway.stopped.get(1, TimeUnit.SECONDS);
	}

	@Test
	public void patternRunsEveryStepAndStops() throws Exception
	{
		connect();
		service.playPattern(new HapticPattern(Arrays.asList(
			new HapticPattern.Step(0.25, Duration.ofMillis(10)),
			new HapticPattern.Step(0.0, Duration.ofMillis(10)),
			new HapticPattern.Step(0.75, Duration.ofMillis(10))
		)));

		assertEquals(0.25, gateway.vibrationCommands.poll(1, TimeUnit.SECONDS), 0.0001);
		assertEquals(0.0, gateway.vibrationCommands.poll(1, TimeUnit.SECONDS), 0.0001);
		assertEquals(0.75, gateway.vibrationCommands.poll(1, TimeUnit.SECONDS), 0.0001);
		gateway.stopped.get(1, TimeUnit.SECONDS);
	}

	@Test
	public void newPatternReplacesRemainingSteps() throws Exception
	{
		connect();
		service.playPattern(new HapticPattern(Arrays.asList(
			new HapticPattern.Step(0.2, Duration.ofMillis(300)),
			new HapticPattern.Step(0.4, Duration.ofMillis(10))
		)));
		assertEquals(0.2, gateway.vibrationCommands.poll(1, TimeUnit.SECONDS), 0.0001);

		service.playPattern(HapticPattern.single(0.9, Duration.ofMillis(20)));

		assertEquals(0.9, gateway.vibrationCommands.poll(1, TimeUnit.SECONDS), 0.0001);
		gateway.stopped.get(1, TimeUnit.SECONDS);
		assertNull(gateway.vibrationCommands.poll(350, TimeUnit.MILLISECONDS));
	}

	@Test
	public void stopNowCancelsRemainingPatternSteps() throws Exception
	{
		connect();
		service.playPattern(new HapticPattern(Arrays.asList(
			new HapticPattern.Step(0.4, Duration.ofMillis(250)),
			new HapticPattern.Step(0.8, Duration.ofMillis(10))
		)));
		assertEquals(0.4, gateway.vibrationCommands.poll(1, TimeUnit.SECONDS), 0.0001);

		service.stopAll();

		gateway.stopped.get(1, TimeUnit.SECONDS);
		assertNull(gateway.vibrationCommands.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test
	public void disconnectPublishesDisconnectedState() throws Exception
	{
		connect();
		CompletableFuture<ConnectionSnapshot> disconnected = awaitState(ConnectionState.DISCONNECTED);

		service.disconnect();

		assertEquals(ConnectionState.DISCONNECTED, disconnected.get(1, TimeUnit.SECONDS).getState());
		gateway.disconnectStarted.get(1, TimeUnit.SECONDS);
		assertTrue(gateway.disconnected);
	}

	@Test
	public void pulseWhileDisconnectedIsIgnored() throws Exception
	{
		CompletableFuture<ConnectionSnapshot> closed = awaitState(ConnectionState.CLOSED);
		service.pulse(0.65, Duration.ofMillis(25));

		service.close();

		closed.get(1, TimeUnit.SECONDS);
		assertFalse(gateway.vibrated.isDone());
	}

	@Test
	public void disconnectCancelsInFlightConnection() throws Exception
	{
		FakeGateway blockingGateway = new FakeGateway(true);
		DefaultIntifaceService blockingService = new DefaultIntifaceService(
			() -> blockingGateway,
			Duration.ofSeconds(5)
		);
		try
		{
			CompletableFuture<ConnectionSnapshot> connecting =
				awaitState(blockingService, ConnectionState.CONNECTING);
			blockingService.connect(new URI("ws://localhost:12345"));
			connecting.get(1, TimeUnit.SECONDS);
			blockingGateway.connectionStarted.get(1, TimeUnit.SECONDS);

			CompletableFuture<ConnectionSnapshot> disconnected =
				awaitState(blockingService, ConnectionState.DISCONNECTED);
			blockingService.disconnect();

			assertEquals(ConnectionState.DISCONNECTED, disconnected.get(1, TimeUnit.SECONDS).getState());
			blockingGateway.disconnectStarted.get(1, TimeUnit.SECONDS);
			assertTrue(blockingGateway.disconnected);
		}
		finally
		{
			blockingService.close();
		}
	}

	@Test
	public void connectionAttemptTimesOut() throws Exception
	{
		FakeGateway blockingGateway = new FakeGateway(true);
		DefaultIntifaceService timedService = new DefaultIntifaceService(
			() -> blockingGateway,
			Duration.ofMillis(25)
		);
		try
		{
			CompletableFuture<ConnectionSnapshot> connecting =
				awaitState(timedService, ConnectionState.CONNECTING);
			timedService.connect(new URI("ws://localhost:12345"));
			connecting.get(1, TimeUnit.SECONDS);

			CompletableFuture<ConnectionSnapshot> disconnected =
				awaitState(timedService, ConnectionState.DISCONNECTED);
			ConnectionSnapshot result = disconnected.get(1, TimeUnit.SECONDS);

			assertTrue(result.getMessage().startsWith("Connection timed out"));
			blockingGateway.disconnectStarted.get(1, TimeUnit.SECONDS);
			assertTrue(blockingGateway.disconnected);
		}
		finally
		{
			timedService.close();
		}
	}

	@Test
	public void blockingGatewayCleanupDoesNotBlockDisconnectedState() throws Exception
	{
		FakeGateway blockingGateway = new FakeGateway(false, true);
		DefaultIntifaceService responsiveService = new DefaultIntifaceService(() -> blockingGateway);
		try
		{
			CompletableFuture<ConnectionSnapshot> connected =
				awaitState(responsiveService, ConnectionState.CONNECTED);
			responsiveService.connect(new URI("ws://localhost:12345"));
			connected.get(1, TimeUnit.SECONDS);

			CompletableFuture<ConnectionSnapshot> disconnected =
				awaitState(responsiveService, ConnectionState.DISCONNECTED);
			responsiveService.disconnect();

			assertEquals(ConnectionState.DISCONNECTED, disconnected.get(1, TimeUnit.SECONDS).getState());
			blockingGateway.disconnectStarted.get(1, TimeUnit.SECONDS);
		}
		finally
		{
			blockingGateway.releaseDisconnect.complete(null);
			responsiveService.close();
		}
	}

	private ConnectionSnapshot connect() throws Exception
	{
		CompletableFuture<ConnectionSnapshot> connected = awaitState(ConnectionState.CONNECTED);
		service.connect(new URI("ws://localhost:12345"));
		return connected.get(1, TimeUnit.SECONDS);
	}

	private CompletableFuture<ConnectionSnapshot> awaitState(ConnectionState expected)
	{
		return awaitState(service, expected);
	}

	private static CompletableFuture<ConnectionSnapshot> awaitState(
		IntifaceService target,
		ConnectionState expected)
	{
		CompletableFuture<ConnectionSnapshot> future = new CompletableFuture<>();
		target.setConnectionListener(snapshot ->
		{
			if (snapshot.getState() == expected)
			{
				future.complete(snapshot);
			}
		});
		return future;
	}

	private static final class FakeGateway implements IntifaceGateway
	{
		private final boolean blockConnection;
		private final boolean blockDisconnect;
		private boolean connected;
		private boolean disconnected;
		private boolean scanningStarted;
		private final CompletableFuture<Void> connectionStarted = new CompletableFuture<>();
		private final CompletableFuture<Void> releaseConnection = new CompletableFuture<>();
		private final CompletableFuture<Void> disconnectStarted = new CompletableFuture<>();
		private final CompletableFuture<Void> releaseDisconnect = new CompletableFuture<>();
		private final CompletableFuture<Double> vibrated = new CompletableFuture<>();
		private final LinkedBlockingQueue<Double> vibrationCommands = new LinkedBlockingQueue<>();
		private final CompletableFuture<Void> stopped = new CompletableFuture<>();

		private FakeGateway()
		{
			this(false, false);
		}

		private FakeGateway(boolean blockConnection)
		{
			this(blockConnection, false);
		}

		private FakeGateway(boolean blockConnection, boolean blockDisconnect)
		{
			this.blockConnection = blockConnection;
			this.blockDisconnect = blockDisconnect;
		}

		@Override
		public void setListener(Listener listener)
		{
		}

		@Override
		public void connect(URI serverUri) throws IntifaceException, InterruptedException
		{
			connectionStarted.complete(null);
			if (blockConnection)
			{
				try
				{
					releaseConnection.get();
				}
				catch (ExecutionException e)
				{
					throw new IntifaceException("Fake connection failed", e);
				}
			}
			connected = true;
		}

		@Override
		public void startScanning()
		{
			scanningStarted = true;
		}

		@Override
		public List<DeviceInfo> getDevices()
		{
			return Collections.singletonList(new DeviceInfo(1, "Test device", true));
		}

		@Override
		public int vibrate(double intensity)
		{
			vibrated.complete(intensity);
			vibrationCommands.offer(intensity);
			return 1;
		}

		@Override
		public void stopAll()
		{
			stopped.complete(null);
		}

		@Override
		public boolean isConnected()
		{
			return connected;
		}

		@Override
		public void disconnect()
		{
			releaseConnection.complete(null);
			connected = false;
			disconnected = true;
			disconnectStarted.complete(null);
			if (blockDisconnect)
			{
				releaseDisconnect.join();
			}
		}
	}
}
