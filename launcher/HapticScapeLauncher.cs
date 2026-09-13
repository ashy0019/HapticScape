using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

internal static class HapticScapeLauncher
{
	[STAThread]
	private static int Main(string[] args)
	{
		Application.EnableVisualStyles();
		Application.SetCompatibleTextRenderingDefault(false);
		ServicePointManager.SecurityProtocol |= (SecurityProtocolType) 3072;

		try
		{
			string applicationDirectory = AppDomain.CurrentDomain.BaseDirectory;
			TryRegisterProtocolHandler(applicationDirectory);
			string deepLink = HapticScapeDeepLink.Read(args);
			ReleaseManifest manifest = ReleaseManifest.Load(
				Path.Combine(applicationDirectory, "app", "release.json"));
			string preferencesPath = UpdatePreferencesStore.GetDefaultPath();

			if (args.Length > 0
				&& string.Equals(args[0], "--update-settings", StringComparison.OrdinalIgnoreCase))
			{
				ShowUpdateSettings(preferencesPath, manifest.Version);
				return 0;
			}
			HapticScapeLaunchOptions launchOptions = deepLink == null
				? HapticScapeLaunchOptions.Parse(args)
				: HapticScapeLaunchOptions.Defaults();
			bool ownsInstance;
			using (Mutex instance = new Mutex(
				true,
				launchOptions.MutexName,
				out ownsInstance))
			{
				string queuedDeepLink = null;
				if (deepLink != null)
				{
					queuedDeepLink = QueueDeepLink(deepLink);
				}
				if (!ownsInstance && deepLink != null)
				{
					ownsInstance = DeepLinkInstanceHandoff.WaitForTakeover(
						instance,
						queuedDeepLink,
						4000
					);
				}
				if (!ownsInstance)
				{
					return 0;
				}

				if (deepLink == null && launchOptions.IsDefault
					&& TryBeginUpdate(applicationDirectory, manifest, preferencesPath))
				{
					return 0;
				}
				if (deepLink == null && launchOptions.IsDefault)
				{
					TryEnsureLumBridge(applicationDirectory, manifest);
				}
				return LaunchClient(applicationDirectory, launchOptions);
			}
		}
		catch (Exception exception)
		{
			MessageBox.Show(exception.Message, "Unable to start HapticScape",
				MessageBoxButtons.OK, MessageBoxIcon.Error);
			return 1;
		}
	}

	private static bool TryBeginUpdate(
		string applicationDirectory,
		ReleaseManifest manifest,
		string preferencesPath)
	{
		UpdatePreferences preferences = UpdatePreferencesStore.Load(preferencesPath);
		bool forcedCheck = preferences.ForceCheck;
		if (!UpdatePolicy.ShouldCheck(preferences, DateTime.UtcNow))
		{
			return false;
		}

		UpdateRelease release;
		try
		{
			release = GitHubReleaseClient.GetLatest(manifest);
			preferences.LastCheckUtc = DateTime.UtcNow;
			preferences.ForceCheck = false;
			SavePreferencesSafely(preferencesPath, preferences);
		}
		catch (Exception exception)
		{
			if (forcedCheck && preferences.UpdateNotifications)
			{
				MessageBox.Show(
					"HapticScape could not check for updates. The installed version will start normally.\n\n"
						+ exception.Message,
					"Update check unavailable", MessageBoxButtons.OK, MessageBoxIcon.Information);
			}
			return false;
		}

		if (!VersionUtility.IsNewer(release.Version, manifest.Version))
		{
			return false;
		}
		if (!forcedCheck
			&& string.Equals(preferences.SkippedVersion, release.Version,
				StringComparison.OrdinalIgnoreCase))
		{
			return false;
		}

		if (!preferences.AutomaticUpdates)
		{
			if (!preferences.UpdateNotifications && !forcedCheck)
			{
				return false;
			}

			using (UpdateAvailableDialog dialog = new UpdateAvailableDialog(
				manifest.Version, release.Version, preferences))
			{
				dialog.ShowDialog();
				preferences.AutomaticUpdates = dialog.AutomaticUpdates;
				preferences.UpdateNotifications = dialog.UpdateNotifications;
				if (dialog.Decision == UpdateDecision.Skip)
				{
					preferences.SkippedVersion = release.Version;
				}
				SavePreferencesSafely(preferencesPath, preferences);
				if (dialog.Decision != UpdateDecision.Install)
				{
					return false;
				}
			}
		}
		else if (preferences.UpdateNotifications)
		{
			MessageBox.Show(
				"HapticScape " + release.Version
					+ " is available and will be installed before the client starts.",
				"HapticScape update", MessageBoxButtons.OK, MessageBoxIcon.Information);
		}

		try
		{
			PreparedUpdate prepared = preferences.UpdateNotifications
				? UpdateProgressDialog.Prepare(release, manifest, applicationDirectory)
				: UpdatePackagePreparer.Prepare(
					release,
					manifest,
					applicationDirectory,
					null);
			StartUpdateHelper(applicationDirectory, prepared);
			return true;
		}
		catch (Exception exception)
		{
			MessageBox.Show(
				"The update was not installed. HapticScape will start the existing version.\n\n"
					+ exception.Message,
				"HapticScape update failed", MessageBoxButtons.OK, MessageBoxIcon.Warning);
			return false;
		}
	}

	private static void TryEnsureLumBridge(
		string applicationDirectory,
		ReleaseManifest manifest)
	{
		if (!LumBridgeSupport.IsRequired(manifest.Version)
			|| LumBridgeSupport.IsInstalled(manifest, applicationDirectory))
		{
			return;
		}

		try
		{
			bool installed = LumBridgeInstallDialog.Ensure(
				manifest,
				applicationDirectory);
			if (installed)
			{
				MessageBox.Show(
					"HapticScape 3 uses LumBridge for RuneLite gameplay events. "
						+ "LumBridge was installed alongside HapticScape and will open now.",
					"LumBridge installed",
					MessageBoxButtons.OK,
					MessageBoxIcon.Information);
				TryLaunchLumBridge(applicationDirectory);
			}
		}
		catch (Exception exception)
		{
			MessageBox.Show(
				"HapticScape was updated successfully, but LumBridge could not be installed automatically. "
					+ "HapticScape will still start normally.\n\n"
					+ exception.Message,
				"LumBridge setup failed",
				MessageBoxButtons.OK,
				MessageBoxIcon.Warning);
		}
	}

	private static void TryLaunchLumBridge(string applicationDirectory)
	{
		try
		{
			string lumBridgeDirectory = Path.Combine(applicationDirectory, "LumBridge");
			string executable = Path.Combine(lumBridgeDirectory, "LumBridge.exe");
			if (!File.Exists(executable))
			{
				return;
			}
			ProcessStartInfo startInfo = new ProcessStartInfo();
			startInfo.FileName = executable;
			startInfo.WorkingDirectory = lumBridgeDirectory;
			startInfo.UseShellExecute = false;
			Process.Start(startInfo);
		}
		catch (Exception)
		{
			// LumBridge is installed; failure to launch it must not block HapticScape.
		}
	}

	private static void StartUpdateHelper(string applicationDirectory, PreparedUpdate prepared)
	{
		ProcessStartInfo startInfo = new ProcessStartInfo();
		startInfo.FileName = prepared.TemporaryUpdaterPath;
		startInfo.Arguments =
			"--parent-pid " + Process.GetCurrentProcess().Id
			+ " --install-dir " + Quote(applicationDirectory.TrimEnd('\\', '/'))
			+ " --staged-dir " + Quote(prepared.StagedApplicationDirectory)
			+ " --temporary-root " + Quote(prepared.TemporaryRoot);
		startInfo.WorkingDirectory = Path.GetTempPath();
		startInfo.UseShellExecute = false;
		Process.Start(startInfo);
	}

	private static int LaunchClient(
		string applicationDirectory,
		HapticScapeLaunchOptions launchOptions)
	{
		string clientJar = Path.Combine(applicationDirectory, "app", "hapticscape-desktop.jar");
		if (!File.Exists(clientJar))
		{
			throw new FileNotFoundException("The HapticScape desktop JAR is missing.", clientJar);
		}

		string javaExecutable = FindBundledJavaExecutable(applicationDirectory);
		if (javaExecutable == null)
		{
			MessageBox.Show(
				"The bundled HapticScape Java runtime is missing or incomplete. Reinstall HapticScape and try again.",
				"HapticScape", MessageBoxButtons.OK, MessageBoxIcon.Error);
			return 1;
		}

		ProcessStartInfo startInfo = new ProcessStartInfo();
		startInfo.FileName = javaExecutable;
		startInfo.Arguments = "-ea -jar \"" + clientJar + "\""
			+ launchOptions.JavaArguments();
		startInfo.WorkingDirectory = applicationDirectory;
		startInfo.UseShellExecute = false;
		using (Process client = Process.Start(startInfo))
		{
			if (client == null)
			{
				throw new InvalidOperationException("The HapticScape client process did not start.");
			}
			client.WaitForExit();
			return client.ExitCode;
		}
	}

	private static void TryRegisterProtocolHandler(string applicationDirectory)
	{
		try
		{
			string executable = Path.Combine(applicationDirectory, "HapticScape.exe");
			using (RegistryKey protocol = Registry.CurrentUser.CreateSubKey(
				@"Software\Classes\hapticscape"))
			{
				protocol.SetValue(null, "URL:HapticScape Protocol");
				protocol.SetValue("URL Protocol", "");
				using (RegistryKey icon = protocol.CreateSubKey("DefaultIcon"))
				{
					icon.SetValue(null, Quote(executable));
				}
				using (RegistryKey command = protocol.CreateSubKey(@"shell\open\command"))
				{
					command.SetValue(null, Quote(executable) + " \"%1\"");
				}
			}
		}
		catch (UnauthorizedAccessException)
		{
			// Protocol registration is a convenience; ordinary launches must still work.
		}
		catch (System.Security.SecurityException)
		{
			// Managed Windows environments may forbid per-user protocol registration.
		}
		catch (IOException)
		{
			// A registry failure must not prevent an ordinary client launch.
		}
	}

	private static string QueueDeepLink(string deepLink)
	{
		string inbox = Path.Combine(
			Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
			"HapticScape",
			"deep-links");
		Directory.CreateDirectory(inbox);
		string name = Guid.NewGuid().ToString("N");
		string temporary = Path.Combine(inbox, name + ".tmp");
		string request = Path.Combine(inbox, name + ".request");
		File.WriteAllText(temporary, deepLink, new System.Text.UTF8Encoding(false));
		File.Move(temporary, request);
		return request;
	}

	private static void ShowUpdateSettings(string preferencesPath, string installedVersion)
	{
		UpdatePreferences preferences = UpdatePreferencesStore.Load(preferencesPath);
		using (UpdateSettingsDialog dialog = new UpdateSettingsDialog(preferences, installedVersion))
		{
			if (dialog.ShowDialog() == DialogResult.OK)
			{
				preferences.AutomaticUpdates = dialog.AutomaticUpdates;
				preferences.UpdateNotifications = dialog.UpdateNotifications;
				if (dialog.CheckNextLaunch)
				{
					preferences.ForceCheck = true;
					preferences.SkippedVersion = null;
					preferences.LastCheckUtc = null;
				}
				UpdatePreferencesStore.Save(preferencesPath, preferences);
			}
		}
	}

	private static void SavePreferencesSafely(
		string preferencesPath,
		UpdatePreferences preferences)
	{
		try
		{
			UpdatePreferencesStore.Save(preferencesPath, preferences);
		}
		catch (Exception)
		{
			// Preference persistence must never prevent the installed client from launching.
		}
	}

	private static string FindBundledJavaExecutable(string applicationDirectory)
	{
		string runtimeBin = Path.Combine(applicationDirectory, "runtime", "bin");
		string[] candidates =
		{
			Path.Combine(runtimeBin, "javaw.exe"),
			Path.Combine(runtimeBin, "java.exe")
		};

		foreach (string candidate in candidates)
		{
			if (File.Exists(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	private static string Quote(string value)
	{
		return "\"" + value.Replace("\"", "\\\"") + "\"";
	}
}

internal enum UpdateDecision
{
	Later,
	Install,
	Skip
}

internal sealed class UpdateAvailableDialog : Form
{
	private readonly CheckBox automaticUpdates = new CheckBox();
	private readonly CheckBox updateNotifications = new CheckBox();

	internal UpdateDecision Decision { get; private set; }
	internal bool AutomaticUpdates { get { return automaticUpdates.Checked; } }
	internal bool UpdateNotifications { get { return updateNotifications.Checked; } }

	internal UpdateAvailableDialog(
		string installedVersion,
		string availableVersion,
		UpdatePreferences preferences)
	{
		Decision = UpdateDecision.Later;
		Text = "HapticScape update available";
		FormBorderStyle = FormBorderStyle.FixedDialog;
		StartPosition = FormStartPosition.CenterScreen;
		MinimizeBox = false;
		MaximizeBox = false;
		ShowInTaskbar = true;
		ClientSize = new Size(450, 210);

		Label heading = new Label();
		heading.AutoSize = false;
		heading.Font = new Font(Font, FontStyle.Bold);
		heading.Text = "HapticScape " + availableVersion + " is available";
		heading.SetBounds(18, 18, 414, 24);
		Controls.Add(heading);

		Label description = new Label();
		description.AutoSize = false;
		description.Text = "You currently have " + installedVersion
			+ ". You can install now, ask again later, or skip only this release.";
		description.SetBounds(18, 46, 414, 38);
		Controls.Add(description);

		automaticUpdates.Text = "Install future updates automatically";
		automaticUpdates.Checked = preferences.AutomaticUpdates;
		automaticUpdates.SetBounds(18, 88, 300, 23);
		Controls.Add(automaticUpdates);

		updateNotifications.Text = "Notify me about future updates";
		updateNotifications.Checked = preferences.UpdateNotifications;
		updateNotifications.SetBounds(18, 113, 300, 23);
		Controls.Add(updateNotifications);

		Button install = MakeButton("Update now", 18, 158, 120);
		install.Click += delegate { Decision = UpdateDecision.Install; Close(); };
		Controls.Add(install);
		AcceptButton = install;

		Button later = MakeButton("Later", 164, 158, 120);
		later.Click += delegate { Decision = UpdateDecision.Later; Close(); };
		Controls.Add(later);

		Button skip = MakeButton("Skip this version", 310, 158, 122);
		skip.Click += delegate { Decision = UpdateDecision.Skip; Close(); };
		Controls.Add(skip);
		CancelButton = later;
	}

	private static Button MakeButton(string text, int x, int y, int width)
	{
		Button button = new Button();
		button.Text = text;
		button.SetBounds(x, y, width, 29);
		return button;
	}
}

internal sealed class UpdateSettingsDialog : Form
{
	private readonly CheckBox automaticUpdates = new CheckBox();
	private readonly CheckBox updateNotifications = new CheckBox();
	private readonly CheckBox checkNextLaunch = new CheckBox();

	internal bool AutomaticUpdates { get { return automaticUpdates.Checked; } }
	internal bool UpdateNotifications { get { return updateNotifications.Checked; } }
	internal bool CheckNextLaunch { get { return checkNextLaunch.Checked; } }

	internal UpdateSettingsDialog(UpdatePreferences preferences, string installedVersion)
	{
		Text = "HapticScape update settings";
		FormBorderStyle = FormBorderStyle.FixedDialog;
		StartPosition = FormStartPosition.CenterScreen;
		MinimizeBox = false;
		MaximizeBox = false;
		ClientSize = new Size(400, 165);

		Label version = new Label();
		version.Text = "Installed version: " + installedVersion;
		version.SetBounds(18, 18, 350, 23);
		Controls.Add(version);

		automaticUpdates.Text = "Install updates automatically";
		automaticUpdates.Checked = preferences.AutomaticUpdates;
		automaticUpdates.SetBounds(18, 47, 300, 23);
		Controls.Add(automaticUpdates);

		updateNotifications.Text = "Notify me when updates are available";
		updateNotifications.Checked = preferences.UpdateNotifications;
		updateNotifications.SetBounds(18, 72, 320, 23);
		Controls.Add(updateNotifications);

		checkNextLaunch.Text = "Check for updates on the next launch";
		checkNextLaunch.SetBounds(18, 97, 320, 23);
		Controls.Add(checkNextLaunch);

		Button save = new Button();
		save.Text = "Save";
		save.DialogResult = DialogResult.OK;
		save.SetBounds(218, 126, 75, 27);
		Controls.Add(save);
		AcceptButton = save;

		Button cancel = new Button();
		cancel.Text = "Cancel";
		cancel.DialogResult = DialogResult.Cancel;
		cancel.SetBounds(305, 126, 75, 27);
		Controls.Add(cancel);
		CancelButton = cancel;
	}
}

internal sealed class LumBridgeInstallDialog : Form
{
	private readonly Label status = new Label();
	private readonly ReleaseManifest manifest;
	private readonly string applicationDirectory;
	private bool installed;
	private Exception failure;

	private LumBridgeInstallDialog(
		ReleaseManifest manifest,
		string applicationDirectory)
	{
		this.manifest = manifest;
		this.applicationDirectory = applicationDirectory;
		Text = "Installing LumBridge";
		FormBorderStyle = FormBorderStyle.FixedDialog;
		StartPosition = FormStartPosition.CenterScreen;
		ControlBox = false;
		ClientSize = new Size(390, 95);

		status.AutoSize = false;
		status.TextAlign = ContentAlignment.MiddleCenter;
		status.Text = "Preparing LumBridge...";
		status.SetBounds(15, 12, 360, 25);
		Controls.Add(status);

		ProgressBar progress = new ProgressBar();
		progress.Style = ProgressBarStyle.Marquee;
		progress.MarqueeAnimationSpeed = 25;
		progress.SetBounds(20, 50, 350, 20);
		Controls.Add(progress);

		Shown += delegate
		{
			Thread worker = new Thread(InstallOnWorker);
			worker.IsBackground = true;
			worker.Name = "LumBridge setup download";
			worker.Start();
		};
	}

	internal static bool Ensure(
		ReleaseManifest manifest,
		string applicationDirectory)
	{
		using (LumBridgeInstallDialog dialog = new LumBridgeInstallDialog(
			manifest,
			applicationDirectory))
		{
			dialog.ShowDialog();
			if (dialog.failure != null)
			{
				throw new InvalidOperationException(
					dialog.failure.Message,
					dialog.failure);
			}
			return dialog.installed;
		}
	}

	private void InstallOnWorker()
	{
		try
		{
			installed = LumBridgeSupport.EnsureInstalled(
				manifest,
				applicationDirectory,
				SetStatus);
		}
		catch (Exception exception)
		{
			failure = exception;
		}
		BeginInvoke((MethodInvoker) Close);
	}

	private void SetStatus(string message)
	{
		if (!IsDisposed)
		{
			BeginInvoke((MethodInvoker) delegate { status.Text = message; });
		}
	}
}

internal sealed class UpdateProgressDialog : Form
{
	private readonly Label status = new Label();
	private readonly UpdateRelease release;
	private readonly ReleaseManifest manifest;
	private readonly string applicationDirectory;
	private PreparedUpdate prepared;
	private Exception failure;

	private UpdateProgressDialog(
		UpdateRelease release,
		ReleaseManifest manifest,
		string applicationDirectory)
	{
		this.release = release;
		this.manifest = manifest;
		this.applicationDirectory = applicationDirectory;
		Text = "Updating HapticScape";
		FormBorderStyle = FormBorderStyle.FixedDialog;
		StartPosition = FormStartPosition.CenterScreen;
		ControlBox = false;
		ClientSize = new Size(390, 95);

		status.AutoSize = false;
		status.TextAlign = ContentAlignment.MiddleCenter;
		status.Text = "Starting update...";
		status.SetBounds(15, 12, 360, 25);
		Controls.Add(status);

		ProgressBar progress = new ProgressBar();
		progress.Style = ProgressBarStyle.Marquee;
		progress.MarqueeAnimationSpeed = 25;
		progress.SetBounds(20, 50, 350, 20);
		Controls.Add(progress);

		Shown += delegate
		{
			Thread worker = new Thread(PrepareOnWorker);
			worker.IsBackground = true;
			worker.Name = "HapticScape update download";
			worker.Start();
		};
	}

	internal static PreparedUpdate Prepare(
		UpdateRelease release,
		ReleaseManifest manifest,
		string applicationDirectory)
	{
		using (UpdateProgressDialog dialog = new UpdateProgressDialog(
			release,
			manifest,
			applicationDirectory))
		{
			dialog.ShowDialog();
			if (dialog.failure != null)
			{
				throw new InvalidOperationException(dialog.failure.Message, dialog.failure);
			}
			if (dialog.prepared == null)
			{
				throw new InvalidOperationException("The update preparation did not complete.");
			}
			return dialog.prepared;
		}
	}

	private void PrepareOnWorker()
	{
		try
		{
			prepared = UpdatePackagePreparer.Prepare(
				release,
				manifest,
				applicationDirectory,
				SetStatus);
		}
		catch (Exception exception)
		{
			failure = exception;
		}
		BeginInvoke((MethodInvoker) Close);
	}

	private void SetStatus(string message)
	{
		if (!IsDisposed)
		{
			BeginInvoke((MethodInvoker) delegate { status.Text = message; });
		}
	}
}
