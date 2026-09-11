using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows.Forms;

internal static class HapticScapeBridgeRuneLiteLauncher
{
    [STAThread]
    private static void Main(string[] args)
    {
        try
        {
            string applicationDirectory = AppDomain.CurrentDomain.BaseDirectory;
            string clientJar = Path.Combine(applicationDirectory, "app", "hapticscape-runelite-bridge-client.jar");
            if (!File.Exists(clientJar))
            {
                throw new FileNotFoundException("The packaged RuneLite bridge client JAR was not found.", clientJar);
            }

            string javaExecutable = FindJavaExecutable(applicationDirectory);
            if (javaExecutable == null)
            {
                MessageBox.Show(
                    "Java was not found. Install the official RuneLite launcher or Java 11+, then try again.",
                    "HapticScape Bridge RuneLite",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
                Environment.ExitCode = 2;
                return;
            }

            StringBuilder arguments = new StringBuilder();
            arguments.Append("-ea -jar ");
            arguments.Append(Quote(clientJar));
            foreach (string argument in args)
            {
                arguments.Append(' ');
                arguments.Append(Quote(argument));
            }

            ProcessStartInfo startInfo = new ProcessStartInfo();
            startInfo.FileName = javaExecutable;
            startInfo.Arguments = arguments.ToString();
            startInfo.WorkingDirectory = applicationDirectory;
            startInfo.UseShellExecute = false;

            Process process = Process.Start(startInfo);
            if (process == null)
            {
                throw new InvalidOperationException("Java did not start.");
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                ex.Message,
                "HapticScape Bridge RuneLite",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
            Environment.ExitCode = 1;
        }
    }

    private static string FindJavaExecutable(string applicationDirectory)
    {
        string localApplicationData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        string[] candidates = new string[]
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

        foreach (string rawEntry in path.Split(Path.PathSeparator))
        {
            try
            {
                string entry = rawEntry.Trim().Trim('"');
                if (entry.Length == 0)
                {
                    continue;
                }
                string candidate = Path.Combine(entry, fileName);
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }
            catch
            {
                // Ignore malformed PATH entries and continue searching.
            }
        }
        return null;
    }

    private static string Quote(string value)
    {
        if (value == null)
        {
            return "\"\"";
        }
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }
}
