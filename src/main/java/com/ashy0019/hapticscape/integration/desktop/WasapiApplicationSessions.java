package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureEndpoint;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Low-level Windows Core Audio session enumeration shared by catalog and meter capture. */
final class WasapiApplicationSessions
{
	private static final GUID CLSID_MMDEVICE_ENUMERATOR =
		new GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
	private static final GUID IID_MMDEVICE_ENUMERATOR =
		new GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");
	private static final GUID IID_AUDIO_SESSION_MANAGER_2 =
		new GUID("{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}");
	private static final GUID IID_AUDIO_SESSION_CONTROL_2 =
		new GUID("{BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D}");
	private static final GUID IID_AUDIO_METER_INFORMATION =
		new GUID("{C02216F6-8C67-4B5B-9D00-D008E73E0064}");

	private WasapiApplicationSessions()
	{
	}

	static List<AudioCaptureApplication> listApplications()
	{
		Map<String, AudioCaptureApplication> applications = new LinkedHashMap<>();
		for (SessionDescriptor descriptor : enumerate(false, null))
		{
			applications.putIfAbsent(
				descriptor.application.getId(),
				descriptor.application
			);
		}
		List<AudioCaptureApplication> result = new ArrayList<>(applications.values());
		result.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(
			left.getDisplayName(),
			right.getDisplayName()
		));
		return result;
	}

	static List<SessionMeter> openMeters(String applicationId)
	{
		List<SessionMeter> meters = new ArrayList<>();
		for (SessionDescriptor descriptor : enumerate(true, applicationId))
		{
			if (descriptor.meter != null)
			{
				meters.add(descriptor.meter);
			}
		}
		return meters;
	}

	static int resolveProcessId(AudioCaptureApplication application)
	{
		String applicationId = application.getId();
		String commandPrefix = "command:";
		if (applicationId.startsWith(commandPrefix))
		{
			String expectedCommand = applicationId.substring(commandPrefix.length());
			int bestProcessId = 0;
			int bestScore = Integer.MIN_VALUE;
			try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses())
			{
				for (ProcessHandle process : (Iterable<ProcessHandle>) processes::iterator)
				{
					if (!process.isAlive() || process.pid() > Integer.MAX_VALUE)
					{
						continue;
					}
					String command = process.info().command().orElse("").trim();
					if (!command.toLowerCase(Locale.ROOT).equals(expectedCommand))
					{
						continue;
					}
					int processId = (int) process.pid();
					String title = WindowsTaskbarWindowNames.titleForProcess(processId);
					int score = processTitleScore(title, application.getDisplayName());
					if (score > bestScore)
					{
						bestScore = score;
						bestProcessId = processId;
					}
				}
			}
			if (bestProcessId > 0)
			{
				return bestProcessId;
			}
		}

		for (SessionDescriptor descriptor : enumerate(false, null))
		{
			if (descriptor.application.getId().equals(applicationId))
			{
				return descriptor.processId;
			}
		}
		return 0;
	}

	static int processTitleScore(String windowTitle, String applicationName)
	{
		String title = windowTitle == null
			? ""
			: windowTitle.trim().toLowerCase(Locale.ROOT);
		String name = applicationName == null
			? ""
			: applicationName.trim().toLowerCase(Locale.ROOT);
		if (title.isEmpty())
		{
			return 0;
		}
		if (!name.isEmpty() && title.equals(name))
		{
			return 100;
		}
		if (!name.isEmpty() && (title.startsWith(name + " ")
			|| title.startsWith(name + " -") || title.contains(name)))
		{
			return 75;
		}
		return 10;
	}

	private static List<SessionDescriptor> enumerate(
		boolean keepMeters,
		String requestedApplicationId)
	{
		List<SessionDescriptor> sessions = new ArrayList<>();
		MmDeviceEnumerator enumerator = null;
		try
		{
			PointerByReference enumeratorPointer = new PointerByReference();
			check(Ole32.INSTANCE.CoCreateInstance(
				CLSID_MMDEVICE_ENUMERATOR,
				Pointer.NULL,
				WTypes.CLSCTX_ALL,
				IID_MMDEVICE_ENUMERATOR,
				enumeratorPointer
			));
			enumerator = new MmDeviceEnumerator(enumeratorPointer.getValue());
			for (AudioCaptureEndpoint endpoint :
				new WasapiAudioEndpointCatalog().listActiveEndpoints())
			{
				if (!endpoint.isSystemDefault())
				{
					enumerateEndpoint(
						enumerator,
						endpoint,
						keepMeters,
						requestedApplicationId,
						sessions
					);
				}
			}
			return sessions;
		}
		finally
		{
			release(enumerator);
		}
	}

	private static void enumerateEndpoint(
		MmDeviceEnumerator enumerator,
		AudioCaptureEndpoint endpoint,
		boolean keepMeters,
		String requestedApplicationId,
		List<SessionDescriptor> result)
	{
		MmDevice device = null;
		AudioSessionManager2 manager = null;
		AudioSessionEnumerator sessions = null;
		try
		{
			PointerByReference devicePointer = new PointerByReference();
			check(enumerator.getDevice(new WString(endpoint.getId()), devicePointer));
			device = new MmDevice(devicePointer.getValue());

			PointerByReference managerPointer = new PointerByReference();
			check(device.activate(
				IID_AUDIO_SESSION_MANAGER_2,
				WTypes.CLSCTX_ALL,
				managerPointer
			));
			manager = new AudioSessionManager2(managerPointer.getValue());
			PointerByReference sessionsPointer = new PointerByReference();
			check(manager.getSessionEnumerator(sessionsPointer));
			sessions = new AudioSessionEnumerator(sessionsPointer.getValue());

			IntByReference count = new IntByReference();
			check(sessions.getCount(count));
			for (int index = 0; index < count.getValue(); index++)
			{
				SessionDescriptor descriptor = describeSession(
					sessions,
					index,
					keepMeters,
					requestedApplicationId
				);
				if (descriptor != null)
				{
					result.add(descriptor);
				}
			}
		}
		catch (RuntimeException ignored)
		{
			// One endpoint can disappear during enumeration without invalidating others.
		}
		finally
		{
			release(sessions);
			release(manager);
			release(device);
		}
	}

	private static SessionDescriptor describeSession(
		AudioSessionEnumerator sessions,
		int index,
		boolean keepMeter,
		String requestedApplicationId)
	{
		AudioSessionControl control = null;
		AudioSessionControl2 control2 = null;
		AudioMeterInformation meter = null;
		try
		{
			PointerByReference controlPointer = new PointerByReference();
			check(sessions.getSession(index, controlPointer));
			control = new AudioSessionControl(controlPointer.getValue());

			PointerByReference control2Pointer = new PointerByReference();
			check(control.queryInterface(IID_AUDIO_SESSION_CONTROL_2, control2Pointer));
			control2 = new AudioSessionControl2(control2Pointer.getValue());
			IntByReference processId = new IntByReference();
			check(control2.getProcessId(processId));
			if (processId.getValue() <= 0)
			{
				return null;
			}

			String sessionName = control.getDisplayName();
			AudioCaptureApplication application = applicationFor(
				processId.getValue(),
				sessionName
			);
			if (application == null)
			{
				return null;
			}
			if (!keepMeter || !application.getId().equals(requestedApplicationId))
			{
				return new SessionDescriptor(application, null, processId.getValue());
			}

			PointerByReference meterPointer = new PointerByReference();
			check(control.queryInterface(IID_AUDIO_METER_INFORMATION, meterPointer));
			meter = new AudioMeterInformation(meterPointer.getValue());
			SessionMeter retained = new SessionMeter(meter);
			meter = null;
			return new SessionDescriptor(application, retained, processId.getValue());
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
		finally
		{
			release(meter);
			release(control2);
			release(control);
		}
	}

	private static AudioCaptureApplication applicationFor(int processId, String sessionName)
	{
		ProcessHandle process = ProcessHandle.of(processId).orElse(null);
		if (process == null || !process.isAlive())
		{
			return null;
		}
		String command = process.info().command().orElse("").trim();
		String displayName = preferredDisplayName(
			sessionName,
			WindowsTaskbarWindowNames.titleForProcess(processId),
			command
		);
		if (displayName.isEmpty())
		{
			displayName = "Process " + processId;
		}
		String id = command.isEmpty()
			? "name:" + displayName.toLowerCase(Locale.ROOT)
			: "command:" + command.toLowerCase(Locale.ROOT);
		return new AudioCaptureApplication(id, displayName);
	}

	static String preferredDisplayName(
		String sessionName,
		String taskbarWindowTitle,
		String command)
	{
		String session = humanSessionName(sessionName);
		String taskbar = humanWindowTitle(taskbarWindowTitle);
		String executable = displayNameFromCommand(command);

		// Hosted desktop applications commonly expose only javaw.exe to Core Audio.
		// In that case the visible top-level window is the identity Windows presents
		// to the user, while the executable path remains the stable capture key.
		if (!taskbar.isEmpty()
			&& (session.isEmpty() || isJavaHost(command)))
		{
			return taskbar;
		}
		if (!session.isEmpty())
		{
			return session;
		}
		if (!taskbar.isEmpty())
		{
			return taskbar;
		}
		return executable;
	}

	private static String humanSessionName(String value)
	{
		String name = value == null ? "" : value.trim();
		return name.isEmpty() || name.startsWith("@") || name.startsWith("{")
			? ""
			: name;
	}

	private static String humanWindowTitle(String value)
	{
		String title = value == null ? "" : value.trim();
		return title.length() > 160 ? title.substring(0, 157) + "..." : title;
	}

	private static boolean isJavaHost(String command)
	{
		String executable = displayNameFromCommand(command).toLowerCase(Locale.ROOT);
		return executable.equals("java") || executable.equals("javaw");
	}

	static String displayNameFromCommand(String command)
	{
		if (command == null || command.trim().isEmpty())
		{
			return "";
		}
		String fileName = command.trim();
		int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		if (separator >= 0 && separator + 1 < fileName.length())
		{
			fileName = fileName.substring(separator + 1);
		}
		if (fileName.toLowerCase(Locale.ROOT).endsWith(".exe"))
		{
			fileName = fileName.substring(0, fileName.length() - 4);
		}
		if (fileName.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(fileName.charAt(0)) + fileName.substring(1);
	}

	static final class SessionMeter implements AutoCloseable
	{
		private AudioMeterInformation meter;

		private SessionMeter(AudioMeterInformation meter)
		{
			this.meter = meter;
		}

		double peak()
		{
			if (meter == null)
			{
				return 0.0;
			}
			FloatByReference value = new FloatByReference();
			check(meter.getPeakValue(value));
			return Math.max(0.0, Math.min(1.0, value.getValue()));
		}

		@Override
		public void close()
		{
			release(meter);
			meter = null;
		}
	}

	private static final class SessionDescriptor
	{
		private final AudioCaptureApplication application;
		private final SessionMeter meter;
		private final int processId;

		private SessionDescriptor(
			AudioCaptureApplication application,
			SessionMeter meter,
			int processId)
		{
			this.application = application;
			this.meter = meter;
			this.processId = processId;
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

	private static class ComUnknown extends Unknown
	{
		private ComUnknown(Pointer pointer)
		{
			super(pointer);
		}

		final HRESULT queryInterface(GUID iid, PointerByReference result)
		{
			return (HRESULT) _invokeNativeObject(0, new Object[] {
				getPointer(), iid, result
			}, HRESULT.class);
		}
	}

	private static final class MmDeviceEnumerator extends ComUnknown
	{
		private MmDeviceEnumerator(Pointer pointer) { super(pointer); }

		private HRESULT getDevice(WString id, PointerByReference device)
		{
			return (HRESULT) _invokeNativeObject(5, new Object[] {
				getPointer(), id, device
			}, HRESULT.class);
		}
	}

	private static final class MmDevice extends ComUnknown
	{
		private MmDevice(Pointer pointer) { super(pointer); }

		private HRESULT activate(GUID iid, int context, PointerByReference result)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), iid, context, Pointer.NULL, result
			}, HRESULT.class);
		}
	}

	private static final class AudioSessionManager2 extends ComUnknown
	{
		private AudioSessionManager2(Pointer pointer) { super(pointer); }

		private HRESULT getSessionEnumerator(PointerByReference result)
		{
			return (HRESULT) _invokeNativeObject(5, new Object[] {
				getPointer(), result
			}, HRESULT.class);
		}
	}

	private static final class AudioSessionEnumerator extends ComUnknown
	{
		private AudioSessionEnumerator(Pointer pointer) { super(pointer); }

		private HRESULT getCount(IntByReference count)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), count
			}, HRESULT.class);
		}

		private HRESULT getSession(int index, PointerByReference result)
		{
			return (HRESULT) _invokeNativeObject(4, new Object[] {
				getPointer(), index, result
			}, HRESULT.class);
		}
	}

	private static class AudioSessionControl extends ComUnknown
	{
		private AudioSessionControl(Pointer pointer) { super(pointer); }

		private String getDisplayName()
		{
			PointerByReference result = new PointerByReference();
			check((HRESULT) _invokeNativeObject(4, new Object[] {
				getPointer(), result
			}, HRESULT.class));
			Pointer text = result.getValue();
			if (text == null)
			{
				return "";
			}
			try
			{
				return text.getWideString(0);
			}
			finally
			{
				Ole32.INSTANCE.CoTaskMemFree(text);
			}
		}
	}

	private static final class AudioSessionControl2 extends ComUnknown
	{
		private AudioSessionControl2(Pointer pointer) { super(pointer); }

		private HRESULT getProcessId(IntByReference processId)
		{
			return (HRESULT) _invokeNativeObject(14, new Object[] {
				getPointer(), processId
			}, HRESULT.class);
		}
	}

	private static final class AudioMeterInformation extends ComUnknown
	{
		private AudioMeterInformation(Pointer pointer) { super(pointer); }

		private HRESULT getPeakValue(FloatByReference value)
		{
			return (HRESULT) _invokeNativeObject(3, new Object[] {
				getPointer(), value
			}, HRESULT.class);
		}
	}
}
