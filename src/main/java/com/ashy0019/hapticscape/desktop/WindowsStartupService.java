package com.ashy0019.hapticscape.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Maintains HapticScape's per-user Windows sign-in entry. */
public final class WindowsStartupService
{
	static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
	static final String VALUE_NAME = "HapticScape";

	public boolean isSupported()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
			&& Files.isRegularFile(executablePath());
	}

	public void apply(boolean enabled, boolean minimized)
	{
		if (!isSupported())
		{
			return;
		}
		try
		{
			ProcessBuilder command;
			if (enabled)
			{
				String value = quote(executablePath().toAbsolutePath().toString())
					+ (minimized ? " --minimized" : "");
				command = new ProcessBuilder(
					"reg.exe", "add", RUN_KEY, "/v", VALUE_NAME,
					"/t", "REG_SZ", "/d", value, "/f"
				);
			}
			else
			{
				command = new ProcessBuilder(
					"reg.exe", "delete", RUN_KEY, "/v", VALUE_NAME, "/f"
				);
			}
			int exit = command.redirectErrorStream(true).start().waitFor();
			if (exit != 0 && enabled)
			{
				throw new IllegalStateException("Windows rejected the startup setting");
			}
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Unable to update Windows startup", failure);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while updating Windows startup", interrupted);
		}
	}

	private static Path executablePath()
	{
		return Paths.get(System.getProperty("user.dir", "."), "HapticScape.exe");
	}

	private static String quote(String value)
	{
		return "\"" + value.replace("\"", "\\\"") + "\"";
	}
}
