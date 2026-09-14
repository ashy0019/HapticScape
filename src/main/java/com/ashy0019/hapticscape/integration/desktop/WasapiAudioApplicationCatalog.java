package com.ashy0019.hapticscape.integration.desktop;

import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureApplicationCatalog;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMUtils;
import java.util.Collections;
import java.util.List;

/** Lists applications currently represented by Windows Core Audio sessions. */
final class WasapiAudioApplicationCatalog implements AudioCaptureApplicationCatalog
{
	@Override
	public List<AudioCaptureApplication> listActiveApplications()
	{
		if (!Platform.isWindows())
		{
			return Collections.emptyList();
		}
		boolean comInitialized = false;
		try
		{
			HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(
				Pointer.NULL,
				Ole32.COINIT_MULTITHREADED
			);
			COMUtils.checkRC(initialized);
			comInitialized = true;
			return WasapiApplicationSessions.listApplications();
		}
		catch (RuntimeException failure)
		{
			throw new IllegalStateException(
				"Unable to list Windows mixer applications",
				failure
			);
		}
		finally
		{
			if (comInitialized)
			{
				Ole32.INSTANCE.CoUninitialize();
			}
		}
	}
}
