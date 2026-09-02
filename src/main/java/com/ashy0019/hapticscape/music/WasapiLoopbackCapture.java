package com.ashy0019.hapticscape.music;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures the Windows default output device through WASAPI loopback. */
public final class WasapiLoopbackCapture implements AudioCaptureSource
{
	private static final GUID CLSID_MMDEVICE_ENUMERATOR =
		new GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
	private static final GUID IID_MMDEVICE_ENUMERATOR =
		new GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");
	private static final GUID IID_AUDIO_CLIENT =
		new GUID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}");
	private static final GUID IID_AUDIO_CAPTURE_CLIENT =
		new GUID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}");

	private static final int E_RENDER = 0;
	private static final int E_CONSOLE = 0;
	private static final int AUDCLNT_SHAREMODE_SHARED = 0;
	private static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
	private static final int AUDCLNT_BUFFERFLAGS_SILENT = 0x00000002;
	private static final long BUFFER_DURATION_100NS = 1_000_000L;
	private static final int WAVE_FORMAT_PCM = 1;
	private static final int WAVE_FORMAT_IEEE_FLOAT = 3;
	private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

	private final AtomicBoolean running = new AtomicBoolean();
	private volatile Thread captureThread;

	@Override
	public void start(Listener listener)
	{
		Objects.requireNonNull(listener, "listener");
		if (!Platform.isWindows())
		{
			throw new UnsupportedOperationException("Music sync requires Windows");
		}
		if (!running.compareAndSet(false, true))
		{
			throw new IllegalStateException("Audio capture is already running");
		}

		Thread thread = new Thread(() -> capture(listener), "hapticscape-audio");
		thread.setDaemon(true);
		captureThread = thread;
		thread.start();
	}

	private void capture(Listener listener)
	{
		MmDeviceEnumerator enumerator = null;
		MmDevice device = null;
		AudioClient audioClient = null;
		AudioCaptureClient captureClient = null;
		Pointer mixFormatPointer = null;
		boolean comInitialized = false;
		try
		{
			HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(
				Pointer.NULL,
				Ole32.COINIT_MULTITHREADED
			);
			COMUtils.checkRC(initialized);
			comInitialized = true;

			PointerByReference enumeratorPointer = new PointerByReference();
			check(Ole32.INSTANCE.CoCreateInstance(
				CLSID_MMDEVICE_ENUMERATOR,
				Pointer.NULL,
				WTypes.CLSCTX_ALL,
				IID_MMDEVICE_ENUMERATOR,
				enumeratorPointer
			));
			enumerator = new MmDeviceEnumerator(enumeratorPointer.getValue());

			PointerByReference devicePointer = new PointerByReference();
			check(enumerator.getDefaultAudioEndpoint(E_RENDER, E_CONSOLE, devicePointer));
			device = new MmDevice(devicePointer.getValue());

			PointerByReference audioClientPointer = new PointerByReference();
			check(device.activate(IID_AUDIO_CLIENT, WTypes.CLSCTX_ALL, audioClientPointer));
			audioClient = new AudioClient(audioClientPointer.getValue());

			PointerByReference mixFormat = new PointerByReference();
			check(audioClient.getMixFormat(mixFormat));
			mixFormatPointer = mixFormat.getValue();
			AudioFormat format = AudioFormat.read(mixFormatPointer);

			check(audioClient.initialize(
				AUDCLNT_SHAREMODE_SHARED,
				AUDCLNT_STREAMFLAGS_LOOPBACK,
				BUFFER_DURATION_100NS,
				0,
				mixFormatPointer
			));

			PointerByReference captureClientPointer = new PointerByReference();
			check(audioClient.getService(IID_AUDIO_CAPTURE_CLIENT, captureClientPointer));
			captureClient = new AudioCaptureClient(captureClientPointer.getValue());
			check(audioClient.startStream());
			listener.onStarted("Listening to Windows system audio");

			while (running.get())
			{
				drainPackets(captureClient, format, listener);
				try
				{
					Thread.sleep(5);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		catch (Throwable error)
		{
			if (running.get())
			{
				listener.onError("Windows audio capture failed", error);
			}
		}
		finally
		{
			running.set(false);
			if (audioClient != null)
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
			release(device);
			release(enumerator);
			if (mixFormatPointer != null)
			{
				Ole32.INSTANCE.CoTaskMemFree(mixFormatPointer);
			}
			if (comInitialized)
			{
				Ole32.INSTANCE.CoUninitialize();
			}
			captureThread = null;
		}
	}

	private static void drainPackets(
		AudioCaptureClient captureClient,
		AudioFormat format,
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
					listener.onSamples(mono, format.sampleRate);
				}
			}
			finally
			{
				check(captureClient.releaseBuffer(frames));
			}
			check(captureClient.getNextPacketSize(nextFrames));
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

	private static final class MmDeviceEnumerator extends Unknown
	{
		private MmDeviceEnumerator(Pointer pointer)
		{
			super(pointer);
		}

		private HRESULT getDefaultAudioEndpoint(
			int dataFlow,
			int role,
			PointerByReference device)
		{
			return (HRESULT) _invokeNativeObject(4, new Object[] {
				getPointer(), dataFlow, role, device
			}, HRESULT.class);
		}
	}

	private static final class MmDevice extends Unknown
	{
		private MmDevice(Pointer pointer)
		{
			super(pointer);
		}

		private HRESULT activate(GUID iid, int context, PointerByReference result)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), iid, context, Pointer.NULL, result
			}, HRESULT.class);
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

		private HRESULT getMixFormat(PointerByReference format)
		{
			return (HRESULT) _invokeNativeObject(8, new Object[] {
				getPointer(), format
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

	static final class AudioFormat
	{
		private final int channels;
		private final int sampleRate;
		private final int blockAlign;
		private final int bitsPerSample;
		private final boolean floatingPoint;

		private AudioFormat(
			int channels,
			int sampleRate,
			int blockAlign,
			int bitsPerSample,
			boolean floatingPoint)
		{
			this.channels = channels;
			this.sampleRate = sampleRate;
			this.blockAlign = blockAlign;
			this.bitsPerSample = bitsPerSample;
			this.floatingPoint = floatingPoint;
		}

		static AudioFormat read(Pointer format)
		{
			int tag = Short.toUnsignedInt(format.getShort(0));
			int channels = Short.toUnsignedInt(format.getShort(2));
			int sampleRate = format.getInt(4);
			int blockAlign = Short.toUnsignedInt(format.getShort(12));
			int bits = Short.toUnsignedInt(format.getShort(14));
			int effectiveTag = tag;
			if (tag == WAVE_FORMAT_EXTENSIBLE)
			{
				effectiveTag = format.getInt(24);
			}
			if (channels < 1 || sampleRate < 1 || blockAlign < 1
				|| (effectiveTag != WAVE_FORMAT_PCM && effectiveTag != WAVE_FORMAT_IEEE_FLOAT))
			{
				throw new IllegalArgumentException("Unsupported Windows audio mix format");
			}
			return new AudioFormat(
				channels,
				sampleRate,
				blockAlign,
				bits,
				effectiveTag == WAVE_FORMAT_IEEE_FLOAT
			);
		}

		float[] toMono(Pointer data, int frames)
		{
			float[] mono = new float[frames];
			int bytesPerChannel = blockAlign / channels;
			for (int frame = 0; frame < frames; frame++)
			{
				double sum = 0.0;
				long frameOffset = (long) frame * blockAlign;
				for (int channel = 0; channel < channels; channel++)
				{
					long offset = frameOffset + (long) channel * bytesPerChannel;
					sum += readSample(data, offset, bytesPerChannel);
				}
				mono[frame] = (float) Math.max(-1.0, Math.min(1.0, sum / channels));
			}
			return mono;
		}

		private double readSample(Pointer data, long offset, int bytesPerChannel)
		{
			if (floatingPoint && bitsPerSample == 32)
			{
				return data.getFloat(offset);
			}
			if (floatingPoint && bitsPerSample == 64)
			{
				return data.getDouble(offset);
			}
			if (bitsPerSample == 16)
			{
				return data.getShort(offset) / 32768.0;
			}
			if (bitsPerSample == 24 && bytesPerChannel == 3)
			{
				int value = (data.getByte(offset) & 0xff)
					| ((data.getByte(offset + 1) & 0xff) << 8)
					| ((data.getByte(offset + 2) & 0xff) << 16);
				if ((value & 0x800000) != 0)
				{
					value |= 0xff000000;
				}
				return value / 8388608.0;
			}
			if (bitsPerSample == 32)
			{
				return data.getInt(offset) / 2147483648.0;
			}
			throw new IllegalArgumentException("Unsupported Windows audio sample encoding");
		}
	}
}
