package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureEndpoint;
import com.ashy0019.hapticscape.music.AudioCaptureEndpointCatalog;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Lists active Windows render endpoints from the Core Audio device registry. */
final class WasapiAudioEndpointCatalog implements AudioCaptureEndpointCatalog
{
	private static final String RENDER_ENDPOINTS =
		"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render";
	private static final String DEVICE_DESCRIPTION =
		"{a45c254e-df1c-4efd-8020-67d146a850e0},2";
	private static final String FRIENDLY_NAME =
		"{a45c254e-df1c-4efd-8020-67d146a850e0},14";
	private static final String INTERFACE_FRIENDLY_NAME =
		"{026e516e-b814-414b-83cd-856d6fef4822},2";
	private static final String ENDPOINT_INTERFACE_NAME =
		"{b3f8fa53-0004-438e-9003-51a46e139bfc},6";
	private static final String RENDER_ENDPOINT_ID_PREFIX = "{0.0.0.00000000}.";
	private static final int DEVICE_STATE_ACTIVE = 1;

	@Override
	public List<AudioCaptureEndpoint> listActiveEndpoints()
	{
		List<AudioCaptureEndpoint> endpoints = new ArrayList<>();
		endpoints.add(AudioCaptureEndpoint.systemDefault());
		if (!Platform.isWindows())
		{
			return endpoints;
		}

		try
		{
			for (String endpointId : Advapi32Util.registryGetKeys(
				WinReg.HKEY_LOCAL_MACHINE,
				RENDER_ENDPOINTS
			))
			{
				String endpointPath = RENDER_ENDPOINTS + "\\" + endpointId;
				if (!Advapi32Util.registryValueExists(
					WinReg.HKEY_LOCAL_MACHINE,
					endpointPath,
					"DeviceState"
				) || Advapi32Util.registryGetIntValue(
					WinReg.HKEY_LOCAL_MACHINE,
					endpointPath,
					"DeviceState"
				) != DEVICE_STATE_ACTIVE)
				{
					continue;
				}

				String propertiesPath = endpointPath + "\\Properties";
				String displayName = windowsDisplayName(
					Advapi32Util.registryGetValues(
						WinReg.HKEY_LOCAL_MACHINE,
						propertiesPath
					)
				);
				endpoints.add(new AudioCaptureEndpoint(
					windowsRenderEndpointId(endpointId),
					displayName
				));
			}
			endpoints.subList(1, endpoints.size()).sort(
				Comparator.comparing(
					AudioCaptureEndpoint::getDisplayName,
					String.CASE_INSENSITIVE_ORDER
				)
			);
			return endpoints;
		}
		catch (RuntimeException failure)
		{
			throw new IllegalStateException("Unable to list Windows audio outputs", failure);
		}
	}

	static String windowsRenderEndpointId(String registryKey)
	{
		String id = registryKey == null ? "" : registryKey.trim();
		if (id.regionMatches(true, 0, "{0.0.", 0, 5))
		{
			return id;
		}
		return RENDER_ENDPOINT_ID_PREFIX + id;
	}

	static String windowsDisplayName(Map<String, Object> properties)
	{
		String primary = stringProperty(properties, DEVICE_DESCRIPTION);
		String detail = firstPresent(
			stringProperty(properties, FRIENDLY_NAME),
			stringProperty(properties, INTERFACE_FRIENDLY_NAME),
			stringProperty(properties, ENDPOINT_INTERFACE_NAME)
		);
		if (primary.isEmpty())
		{
			primary = detail;
			detail = "";
		}
		if (primary.isEmpty())
		{
			primary = firstHumanReadableValue(properties);
		}
		if (primary.isEmpty())
		{
			return "Windows output";
		}

		detail = removeRepeatedPrimary(primary, detail);
		return detail.isEmpty() ? primary : primary + " — " + detail;
	}

	private static String stringProperty(Map<String, Object> properties, String propertyName)
	{
		for (Map.Entry<String, Object> entry : properties.entrySet())
		{
			if (propertyName.equalsIgnoreCase(entry.getKey())
				&& entry.getValue() instanceof String)
			{
				return ((String) entry.getValue()).trim();
			}
		}
		return "";
	}

	private static String firstPresent(String... values)
	{
		for (String value : values)
		{
			if (value != null && !value.trim().isEmpty())
			{
				return value.trim();
			}
		}
		return "";
	}

	private static String firstHumanReadableValue(Map<String, Object> properties)
	{
		for (Object value : properties.values())
		{
			if (value instanceof String)
			{
				String text = ((String) value).trim();
				if (!text.isEmpty() && text.length() <= 160
					&& !text.startsWith("@") && !text.startsWith("{"))
				{
					return text;
				}
			}
		}
		return "";
	}

	private static String removeRepeatedPrimary(String primary, String detail)
	{
		if (detail.isEmpty() || detail.equalsIgnoreCase(primary))
		{
			return "";
		}
		String prefix = primary + " (";
		if (detail.regionMatches(true, 0, prefix, 0, prefix.length())
			&& detail.endsWith(")"))
		{
			return detail.substring(prefix.length(), detail.length() - 1).trim();
		}
		return detail;
	}
}
