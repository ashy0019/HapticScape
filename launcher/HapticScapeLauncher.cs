using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class HapticScapeLauncher
{
	[STAThread]
	private static int Main()
	{
		try
		{
			string applicationDirectory = AppDomain.CurrentDomain.BaseDirectory;
			string clientJar = Path.Combine(applicationDirectory, "app", "hapticscape-client.jar");
			if (!File.Exists(clientJar))
			{
				throw new FileNotFoundException("The HapticScape client JAR is missing.", clientJar);
			}

			string javaExecutable = FindJavaExecutable(applicationDirectory);
			if (javaExecutable == null)
			{
				MessageBox.Show(
					"The official RuneLite Java runtime was not found. Install RuneLite from runelite.net and try again.",
					"HapticScape",
					MessageBoxButtons.OK,
					MessageBoxIcon.Error);
				return 1;
			}

			ProcessStartInfo startInfo = new ProcessStartInfo();
			startInfo.FileName = javaExecutable;
			startInfo.Arguments = "-ea -jar \"" + clientJar + "\" --developer-mode --debug";
			startInfo.WorkingDirectory = applicationDirectory;
			startInfo.UseShellExecute = false;
			Process.Start(startInfo);
			return 0;
		}
		catch (Exception exception)
		{
			MessageBox.Show(
				exception.Message,
				"Unable to start HapticScape",
				MessageBoxButtons.OK,
				MessageBoxIcon.Error);
			return 1;
		}
	}

	private static string FindJavaExecutable(string applicationDirectory)
	{
		string localApplicationData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
		string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
		string[] candidates =
		{
			Path.Combine(applicationDirectory, "runtime", "bin", "javaw.exe"),
			Path.Combine(localApplicationData, "RuneLite", "jre", "bin", "javaw.exe"),
			Path.Combine(localApplicationData, "RuneLite", "jre", "bin", "java.exe"),
			string.IsNullOrEmpty(javaHome) ? null : Path.Combine(javaHome, "bin", "javaw.exe"),
			string.IsNullOrEmpty(javaHome) ? null : Path.Combine(javaHome, "bin", "java.exe"),
			FindOnPath("javaw.exe"),
			FindOnPath("java.exe")
		};

		foreach (string candidate in candidates)
		{
			if (!string.IsNullOrEmpty(candidate) && File.Exists(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	private static string FindOnPath(string fileName)
	{
		string path = Environment.GetEnvironmentVariable("PATH");
		if (string.IsNullOrEmpty(path))
		{
			return null;
		}

		foreach (string directory in path.Split(Path.PathSeparator))
		{
			try
			{
				string candidate = Path.Combine(directory.Trim(), fileName);
				if (File.Exists(candidate))
				{
					return candidate;
				}
			}
			catch (Exception)
			{
				// Ignore malformed PATH entries and continue searching.
			}
		}
		return null;
	}
}
