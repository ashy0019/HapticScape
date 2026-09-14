package com.ashy0019.hapticscape.integration.desktop;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import java.util.ArrayList;
import java.util.List;

/** Resolves the visible top-level window name Windows presents for a process. */
final class WindowsTaskbarWindowNames
{
	private static final int MAXIMUM_TITLE_LENGTH = 2048;
	private static final int WS_EX_TOOLWINDOW = 0x00000080;

	private WindowsTaskbarWindowNames()
	{
	}

	static String titleForProcess(int processId)
	{
		if (processId <= 0)
		{
			return "";
		}
		List<WindowTitle> candidates = new ArrayList<>();
		try
		{
			User32.INSTANCE.EnumWindows((window, ignored) ->
			{
				collectCandidate(window, processId, candidates);
				return true;
			}, Pointer.NULL);
		}
		catch (RuntimeException | LinkageError ignored)
		{
			return "";
		}

		for (WindowTitle candidate : candidates)
		{
			if (candidate.taskbarWindow)
			{
				return candidate.title;
			}
		}
		return candidates.isEmpty() ? "" : candidates.get(0).title;
	}

	private static void collectCandidate(
		HWND window,
		int requestedProcessId,
		List<WindowTitle> candidates)
	{
		if (!User32.INSTANCE.IsWindowVisible(window))
		{
			return;
		}
		IntByReference processId = new IntByReference();
		User32.INSTANCE.GetWindowThreadProcessId(window, processId);
		if (processId.getValue() != requestedProcessId)
		{
			return;
		}

		int length = User32.INSTANCE.GetWindowTextLength(window);
		if (length <= 0)
		{
			return;
		}
		char[] text = new char[Math.min(length + 1, MAXIMUM_TITLE_LENGTH)];
		if (User32.INSTANCE.GetWindowText(window, text, text.length) <= 0)
		{
			return;
		}
		String title = Native.toString(text).trim();
		if (title.isEmpty())
		{
			return;
		}

		HWND owner = User32.INSTANCE.GetWindow(window, new DWORD(WinUser.GW_OWNER));
		boolean hasOwner = owner != null
			&& owner.getPointer() != null
			&& Pointer.nativeValue(owner.getPointer()) != 0;
		boolean toolWindow = (User32.INSTANCE.GetWindowLong(
			window,
			WinUser.GWL_EXSTYLE
		) & WS_EX_TOOLWINDOW) != 0;
		candidates.add(new WindowTitle(title, !hasOwner && !toolWindow));
	}

	private static final class WindowTitle
	{
		private final String title;
		private final boolean taskbarWindow;

		private WindowTitle(String title, boolean taskbarWindow)
		{
			this.title = title;
			this.taskbarWindow = taskbarWindow;
		}
	}
}
