package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureSource;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.COM.UnknownVTable;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Captures real PCM rendered by one Windows process tree. */
public final class WasapiApplicationLoopbackCapture implements AudioCaptureSource
{
	private static final String PROCESS_LOOPBACK_DEVICE = "VAD\\Process_Loopback";
	private static final GUID IID_AUDIO_CLIENT =
		new GUID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}");
	private static final GUID IID_AUDIO_CAPTURE_CLIENT =
		new GUID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}");
	private static final long BUFFER_DURATION_100NS = 1_000_000L;
	private static final int AUDCLNT_SHAREMODE_SHARED = 0;
	private static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
	private static final int AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM = 0x80000000;
	private static final int AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY = 0x08000000;
	private static final int AUDCLNT_BUFFERFLAGS_SILENT = 0x00000002;
	private static final int WAVE_FORMAT_IEEE_FLOAT = 3;
	private static final int PROCESS_CHANNELS = 2;
	private static final int PROCESS_SAMPLE_RATE = 48_000;
	private static final int PROCESS_BITS_PER_SAMPLE = 32;
	private static final int PROCESS_BLOCK_ALIGN =
		PROCESS_CHANNELS * PROCESS_BITS_PER_SAMPLE / 8;
	private static final long PROCESS_POLL_NANOS = 250_000_000L;
	private static final long PROCESS_RETRY_MILLIS = 500L;

	private final AudioCaptureApplication application;
	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Thread captureThread;

	WasapiApplicationLoopbackCapture(AudioCaptureApplication application)
	{
		this.application = Objects.requireNonNull(application, "application");
	}

	@Override
	public void start(Listener listener)
	{
		Objects.requireNonNull(listener, "listener");
		if (!com.sun.jna.Platform.isWindows())
		{
			throw new UnsupportedOperationException("Application audio capture requires Windows");
		}
		if (!running.compareAndSet(false, true))
		{
			throw new IllegalStateException("Application audio capture is already running");
		}
		Thread thread = new Thread(() -> capture(listener), "hapticscape-app-audio");
		thread.setDaemon(true);
		captureThread = thread;
		thread.start();
	}

	private void capture(Listener listener)
	{
		boolean comInitialized = false;
		try
		{
			HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(
				Pointer.NULL,
				Ole32.COINIT_MULTITHREADED
			);
			COMUtils.checkRC(initialized);
			comInitialized = true;

			boolean waitingAnnounced = false;
			while (running.get())
			{
				int processId = WasapiApplicationSessions.resolveProcessId(application);
				if (processId <= 0)
				{
					if (!waitingAnnounced)
					{
						listener.onStarted("Waiting for " + application.getDisplayName());
						waitingAnnounced = true;
					}
					if (!pause(PROCESS_RETRY_MILLIS))
					{
						break;
					}
					continue;
				}

				waitingAnnounced = false;
				captureProcess(processId, listener);
				if (running.get() && !pause(PROCESS_RETRY_MILLIS))
				{
					break;
				}
			}
		}
		catch (Throwable failure)
		{
			if (running.get())
			{
				listener.onError(
					"Per-application audio capture requires Windows 10 build 20348 or newer",
					failure
				);
			}
		}
		finally
		{
			running.set(false);
			if (comInitialized)
			{
				Ole32.INSTANCE.CoUninitialize();
			}
			captureThread = null;
		}
	}

	private void captureProcess(int processId, Listener listener)
	{
		ActivateAudioOperation operation = null;
		AudioClient audioClient = null;
		AudioCaptureClient captureClient = null;
		boolean streamStarted = false;
		try
		{
			AudioClientActivationParams activation =
				AudioClientActivationParams.includeProcessTree(processId);
			ProcessLoopbackPropVariant parameters =
				ProcessLoopbackPropVariant.forActivation(activation);
			ActivationCompletionHandler completion = new ActivationCompletionHandler();
			PointerByReference operationPointer = new PointerByReference();
			check(MmDeviceApi.INSTANCE.ActivateAudioInterfaceAsync(
				new WString(PROCESS_LOOPBACK_DEVICE),
				IID_AUDIO_CLIENT,
				parameters,
				completion.getPointer(),
				operationPointer
			));
			operation = new ActivateAudioOperation(operationPointer.getValue());
			Pointer clientPointer = completion.awaitAudioClient();
			audioClient = new AudioClient(clientPointer);

			Memory mixFormatPointer = requestedFormat();
			WasapiLoopbackCapture.AudioFormat format =
				WasapiLoopbackCapture.AudioFormat.read(mixFormatPointer);
			check(audioClient.initialize(
				AUDCLNT_SHAREMODE_SHARED,
				AUDCLNT_STREAMFLAGS_LOOPBACK
					| AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM
					| AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
				BUFFER_DURATION_100NS,
				0,
				mixFormatPointer
			));

			PointerByReference capturePointer = new PointerByReference();
			check(audioClient.getService(IID_AUDIO_CAPTURE_CLIENT, capturePointer));
			captureClient = new AudioCaptureClient(capturePointer.getValue());
			check(audioClient.startStream());
			streamStarted = true;
			listener.onStarted("Listening to " + application.getDisplayName());

			long nextProcessPoll = 0L;
			while (running.get())
			{
				drainPackets(captureClient, format, listener);
				long now = System.nanoTime();
				if (now >= nextProcessPoll)
				{
					if (!ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false))
					{
						break;
					}
					nextProcessPoll = now + PROCESS_POLL_NANOS;
				}
				if (!pause(5L))
				{
					break;
				}
			}
		}
		finally
		{
			if (audioClient != null && streamStarted)
			{
				try
				{
					audioClient.stopStream();
				}
				catch (RuntimeException ignored)
				{
				}
			}
			release(captureClient);
			release(audioClient);
			release(operation);
		}
	}

	static Memory requestedFormat()
	{
		Memory format = new Memory(18);
		format.clear();
		format.setShort(0, (short) WAVE_FORMAT_IEEE_FLOAT);
		format.setShort(2, (short) PROCESS_CHANNELS);
		format.setInt(4, PROCESS_SAMPLE_RATE);
		format.setInt(8, PROCESS_SAMPLE_RATE * PROCESS_BLOCK_ALIGN);
		format.setShort(12, (short) PROCESS_BLOCK_ALIGN);
		format.setShort(14, (short) PROCESS_BITS_PER_SAMPLE);
		format.setShort(16, (short) 0);
		return format;
	}

	private static void drainPackets(
		AudioCaptureClient captureClient,
		WasapiLoopbackCapture.AudioFormat format,
		Listener listener)
	{
		IntByReference nextFrames = new IntByReference();
		check(captureClient.getNextPacketSize(nextFrames));
		while (nextFrames.getValue() > 0)
		{
			PointerByReference data = new PointerByReference();
			IntByReference frameCount = new IntByReference();
			IntByReference flags = new IntByReference();
			check(captureClient.getBuffer(data, frameCount, flags));
			int frames = frameCount.getValue();
			try
			{
				float[] mono = (flags.getValue() & AUDCLNT_BUFFERFLAGS_SILENT) != 0
					? new float[frames]
					: format.toMono(data.getValue(), frames);
				if (mono.length > 0)
				{
					listener.onSamples(mono, format.sampleRate(), 1.0);
				}
			}
			finally
			{
				check(captureClient.releaseBuffer(frames));
			}
			check(captureClient.getNextPacketSize(nextFrames));
		}
	}

	private boolean pause(long milliseconds)
	{
		try
		{
			Thread.sleep(milliseconds);
			return running.get();
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Override
	public void close()
	{
		running.set(false);
		Thread thread = captureThread;
		if (thread != null)
		{
			thread.interrupt();
		}
	}

	private static void check(HRESULT result)
	{
		COMUtils.checkRC(result);
	}

	private static void release(Unknown value)
	{
		if (value != null)
		{
			value.Release();
		}
	}

	private interface MmDeviceApi extends StdCallLibrary
	{
		MmDeviceApi INSTANCE = Native.load(
			"Mmdevapi",
			MmDeviceApi.class,
			W32APIOptions.DEFAULT_OPTIONS
		);

		HRESULT ActivateAudioInterfaceAsync(
			WString deviceInterfacePath,
			GUID interfaceId,
			ProcessLoopbackPropVariant activationParameters,
			Pointer completionHandler,
			PointerByReference activationOperation);
	}

	@Structure.FieldOrder({"activationType", "targetProcessId", "processLoopbackMode"})
	public static final class AudioClientActivationParams extends Structure
	{
		private static final int PROCESS_LOOPBACK_ACTIVATION = 1;
		private static final int INCLUDE_TARGET_PROCESS_TREE = 0;

		public int activationType;
		public int targetProcessId;
		public int processLoopbackMode;

		static AudioClientActivationParams includeProcessTree(int processId)
		{
			AudioClientActivationParams result = new AudioClientActivationParams();
			result.activationType = PROCESS_LOOPBACK_ACTIVATION;
			result.targetProcessId = processId;
			result.processLoopbackMode = INCLUDE_TARGET_PROCESS_TREE;
			result.write();
			return result;
		}
	}

	@Structure.FieldOrder({"size", "data"})
	public static final class Blob extends Structure
	{
		public int size;
		public Pointer data;
	}

	@Structure.FieldOrder({"type", "reserved1", "reserved2", "reserved3", "blob"})
	public static final class ProcessLoopbackPropVariant extends Structure
	{
		private static final short VT_BLOB = 65;

		public short type;
		public short reserved1;
		public short reserved2;
		public short reserved3;
		public Blob blob;
		private AudioClientActivationParams activation;

		static ProcessLoopbackPropVariant forActivation(
			AudioClientActivationParams activation)
		{
			ProcessLoopbackPropVariant result = new ProcessLoopbackPropVariant();
			result.activation = activation;
			result.type = VT_BLOB;
			result.blob = new Blob();
			result.blob.size = activation.size();
			result.blob.data = activation.getPointer();
			result.blob.write();
			result.write();
			return result;
		}
	}

	private static final class AudioClient extends Unknown
	{
		private AudioClient(Pointer pointer)
		{
			super(pointer);
		}

		private HRESULT initialize(
			int shareMode,
			int streamFlags,
			long bufferDuration,
			long periodicity,
			Pointer format)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), shareMode, streamFlags, bufferDuration, periodicity,
				format, Pointer.NULL
			}, HRESULT.class);
		}

		private HRESULT startStream()
		{
			return (HRESULT) _invokeNativeObject(10, new Object[] {getPointer()}, HRESULT.class);
		}

		private HRESULT stopStream()
		{
			return (HRESULT) _invokeNativeObject(11, new Object[] {getPointer()}, HRESULT.class);
		}

		private HRESULT getService(GUID iid, PointerByReference service)
		{
			return (HRESULT) _invokeNativeObject(14, new Object[] {
				getPointer(), iid, service
			}, HRESULT.class);
		}
	}

	private static final class AudioCaptureClient extends Unknown
	{
		private AudioCaptureClient(Pointer pointer)
		{
			super(pointer);
		}

		private HRESULT getBuffer(
			PointerByReference data,
			IntByReference frames,
			IntByReference flags)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), data, frames, flags, Pointer.NULL, Pointer.NULL
			}, HRESULT.class);
		}

		private HRESULT releaseBuffer(int frames)
		{
			return (HRESULT) _invokeNativeObject(4, new Object[] {
				getPointer(), frames
			}, HRESULT.class);
		}

		private HRESULT getNextPacketSize(IntByReference frames)
		{
			return (HRESULT) _invokeNativeObject(5, new Object[] {
				getPointer(), frames
			}, HRESULT.class);
		}
	}

	private static final class ActivateAudioOperation extends Unknown
	{
		private ActivateAudioOperation(Pointer pointer)
		{
			super(pointer);
		}

		private HRESULT getActivateResult(
			IntByReference activationResult,
			PointerByReference activatedInterface)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), activationResult, activatedInterface
			}, HRESULT.class);
		}
	}

	public interface ActivateCompletedCallback extends StdCallLibrary.StdCallCallback
	{
		HRESULT invoke(Pointer thisPointer, Pointer activationOperation);
	}

	@Structure.FieldOrder({
		"queryInterface", "addRef", "release", "activateCompleted"
	})
	public static class ActivationCompletionVTable extends Structure
	{
		public UnknownVTable.QueryInterfaceCallback queryInterface;
		public UnknownVTable.AddRefCallback addRef;
		public UnknownVTable.ReleaseCallback release;
		public ActivateCompletedCallback activateCompleted;

		public static final class ByReference extends ActivationCompletionVTable
			implements Structure.ByReference
		{
		}
	}

	@Structure.FieldOrder({"vtable"})
	public static final class ActivationCompletionHandler extends Structure
	{
		private static final GUID IID_UNKNOWN =
			new GUID("{00000000-0000-0000-C000-000000000046}");
		private static final GUID IID_AGILE_OBJECT =
			new GUID("{94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}");
		private static final GUID IID_COMPLETION_HANDLER =
			new GUID("{41D949AB-9862-444A-80F6-C261334DA5EB}");
		private static final long ACTIVATION_TIMEOUT_SECONDS = 10L;

		public ActivationCompletionVTable.ByReference vtable;
		private final AtomicInteger references = new AtomicInteger(1);
		private final CountDownLatch completed = new CountDownLatch(1);
		private volatile Pointer audioClient;
		private volatile Throwable failure;

		private ActivationCompletionHandler()
		{
			vtable = new ActivationCompletionVTable.ByReference();
			vtable.queryInterface = this::queryInterface;
			vtable.addRef = ignored -> references.incrementAndGet();
			vtable.release = ignored -> references.updateAndGet(value -> Math.max(0, value - 1));
			vtable.activateCompleted = this::activateCompleted;
			vtable.write();
			write();
		}

		private HRESULT queryInterface(
			Pointer ignored,
			REFIID requested,
			PointerByReference result)
		{
			if (matches(requested, IID_UNKNOWN)
				|| matches(requested, IID_AGILE_OBJECT)
				|| matches(requested, IID_COMPLETION_HANDLER))
			{
				result.setValue(getPointer());
				references.incrementAndGet();
				return WinError.S_OK;
			}
			result.setValue(Pointer.NULL);
			return new HRESULT(WinError.E_NOINTERFACE);
		}

		private HRESULT activateCompleted(Pointer ignored, Pointer operationPointer)
		{
			try
			{
				ActivateAudioOperation operation =
					new ActivateAudioOperation(operationPointer);
				IntByReference activationResult = new IntByReference();
				PointerByReference activatedInterface = new PointerByReference();
				check(operation.getActivateResult(
					activationResult,
					activatedInterface
				));
				check(new HRESULT(activationResult.getValue()));
				audioClient = activatedInterface.getValue();
			}
			catch (Throwable error)
			{
				failure = error;
			}
			finally
			{
				completed.countDown();
			}
			return WinError.S_OK;
		}

		private Pointer awaitAudioClient()
		{
			try
			{
				if (!completed.await(ACTIVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Windows application audio activation timed out");
				}
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Windows application audio activation interrupted", interrupted);
			}
			if (failure instanceof RuntimeException)
			{
				throw (RuntimeException) failure;
			}
			if (failure != null)
			{
				throw new IllegalStateException("Windows application audio activation failed", failure);
			}
			if (audioClient == null || Pointer.nativeValue(audioClient) == 0)
			{
				throw new IllegalStateException("Windows returned no application audio client");
			}
			return audioClient;
		}

		private static boolean matches(REFIID requested, GUID expected)
		{
			return requested != null && requested.getValue() != null
				&& requested.getValue().toGuidString().equalsIgnoreCase(expected.toGuidString());
		}
	}
}
