using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;

internal sealed class ReleaseManifest
{
	internal string Version { get; private set; }
	internal string Architecture { get; private set; }
	internal string Repository { get; private set; }

	private ReleaseManifest(string version, string architecture, string repository)
	{
		Version = version;
		Architecture = architecture;
		Repository = repository;
	}

	internal static ReleaseManifest Load(string path)
	{
		if (!File.Exists(path))
		{
			throw new FileNotFoundException("The HapticScape release manifest is missing.", path);
		}

		Dictionary<string, object> values = JsonValues.ParseObject(File.ReadAllText(path));
		string version = JsonValues.RequiredString(values, "version");
		string architecture = JsonValues.RequiredString(values, "architecture");
		string repository = JsonValues.RequiredString(values, "repository");

		System.Version parsedVersion;
		if (!VersionUtility.TryParseStable(version, out parsedVersion))
		{
			throw new InvalidDataException("The installed HapticScape version is invalid.");
		}
		if (string.IsNullOrWhiteSpace(architecture))
		{
			throw new InvalidDataException("The installed HapticScape architecture is missing.");
		}
		if (!string.Equals(repository, UpdateConstants.Repository, StringComparison.Ordinal))
		{
			throw new InvalidDataException("The release manifest names an unexpected repository.");
		}

		return new ReleaseManifest(version, architecture, repository);
	}
}

internal sealed class UpdatePreferences
{
	internal bool AutomaticUpdates { get; set; }
	internal bool UpdateNotifications { get; set; }
	internal string SkippedVersion { get; set; }
	internal DateTime? LastCheckUtc { get; set; }
	internal bool ForceCheck { get; set; }

	internal static UpdatePreferences Defaults()
	{
		return new UpdatePreferences
		{
			AutomaticUpdates = false,
			UpdateNotifications = true,
			SkippedVersion = null,
			LastCheckUtc = null,
			ForceCheck = false
		};
	}
}

internal static class UpdatePreferencesStore
{
	internal static string GetDefaultPath()
	{
		return Path.Combine(
			Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
			".runelite",
			"hapticscape",
			"updater-settings.json");
	}

	internal static UpdatePreferences Load(string path)
	{
		UpdatePreferences preferences = UpdatePreferences.Defaults();
		if (!File.Exists(path))
		{
			return preferences;
		}

		try
		{
			Dictionary<string, object> values = JsonValues.ParseObject(File.ReadAllText(path));
			preferences.AutomaticUpdates = JsonValues.OptionalBoolean(
				values,
				"automaticUpdates",
				preferences.AutomaticUpdates);
			preferences.UpdateNotifications = JsonValues.OptionalBoolean(
				values,
				"updateNotifications",
				preferences.UpdateNotifications);
			preferences.SkippedVersion = JsonValues.OptionalString(values, "skippedVersion");
			preferences.ForceCheck = JsonValues.OptionalBoolean(values, "forceCheck", false);

			string lastCheck = JsonValues.OptionalString(values, "lastCheckUtc");
			DateTime parsed;
			if (!string.IsNullOrEmpty(lastCheck)
				&& DateTime.TryParse(
					lastCheck,
					CultureInfo.InvariantCulture,
					DateTimeStyles.AdjustToUniversal | DateTimeStyles.AssumeUniversal,
					out parsed))
			{
				preferences.LastCheckUtc = parsed.ToUniversalTime();
			}
		}
		catch (Exception)
		{
			// A damaged preference file must never prevent the client from starting.
		}
		return preferences;
	}

	internal static void Save(string path, UpdatePreferences preferences)
	{
		string directory = Path.GetDirectoryName(path);
		if (string.IsNullOrEmpty(directory))
		{
			throw new InvalidOperationException("The updater settings path has no parent directory.");
		}
		Directory.CreateDirectory(directory);

		Dictionary<string, object> values = new Dictionary<string, object>();
		values["automaticUpdates"] = preferences.AutomaticUpdates;
		values["updateNotifications"] = preferences.UpdateNotifications;
		values["skippedVersion"] = preferences.SkippedVersion;
		values["lastCheckUtc"] = preferences.LastCheckUtc.HasValue
			? preferences.LastCheckUtc.Value.ToUniversalTime().ToString("o", CultureInfo.InvariantCulture)
			: null;
		values["forceCheck"] = preferences.ForceCheck;

		string temporaryPath = path + ".tmp";
		File.WriteAllText(
			temporaryPath,
			new JavaScriptSerializer().Serialize(values),
			new UTF8Encoding(false));
		File.Copy(temporaryPath, path, true);
		File.Delete(temporaryPath);
	}
}

internal sealed class UpdateRelease
{
	internal string Version { get; private set; }
	internal string ZipName { get; private set; }
	internal Uri ZipUri { get; private set; }
	internal Uri ChecksumUri { get; private set; }

	internal UpdateRelease(string version, string zipName, Uri zipUri, Uri checksumUri)
	{
		Version = version;
		ZipName = zipName;
		ZipUri = zipUri;
		ChecksumUri = checksumUri;
	}
}

internal static class GitHubReleaseClient
{
	internal static UpdateRelease GetLatest(ReleaseManifest manifest)
	{
		Uri apiUri = new Uri(
			"https://api.github.com/repos/" + manifest.Repository + "/releases/latest");
		string json;
		using (TimeoutWebClient client = CreateClient(UpdateConstants.CheckTimeoutMillis))
		{
			json = client.DownloadString(apiUri);
		}
		return ParseLatest(json, manifest.Architecture);
	}

	internal static UpdateRelease ParseLatest(string json, string architecture)
	{
		Dictionary<string, object> release = JsonValues.ParseObject(json);
		if (JsonValues.OptionalBoolean(release, "draft", false)
			|| JsonValues.OptionalBoolean(release, "prerelease", false))
		{
			throw new InvalidDataException("GitHub returned a draft or prerelease as the stable release.");
		}

		string tag = JsonValues.RequiredString(release, "tag_name");
		Version parsed;
		if (!VersionUtility.TryParseStable(tag, out parsed))
		{
			throw new InvalidDataException("The latest GitHub release does not have a stable numeric version.");
		}
		string version = VersionUtility.WithoutPrefix(tag);
		string zipName = "HapticScape-Windows-" + architecture + "-" + version + ".zip";
		string checksumName = zipName + ".sha256";

		object assetsValue;
		if (!release.TryGetValue("assets", out assetsValue))
		{
			throw new InvalidDataException("The latest GitHub release has no assets.");
		}
		object[] assets = assetsValue as object[];
		if (assets == null)
		{
			throw new InvalidDataException("The latest GitHub release assets are malformed.");
		}

		Uri zipUri = null;
		Uri checksumUri = null;
		foreach (object assetValue in assets)
		{
			Dictionary<string, object> asset = assetValue as Dictionary<string, object>;
			if (asset == null)
			{
				continue;
			}
			string name = JsonValues.OptionalString(asset, "name");
			string downloadUrl = JsonValues.OptionalString(asset, "browser_download_url");
			Uri uri;
			if (string.IsNullOrEmpty(downloadUrl)
				|| !Uri.TryCreate(downloadUrl, UriKind.Absolute, out uri)
				|| !IsTrustedDownloadUri(uri))
			{
				continue;
			}
			if (string.Equals(name, zipName, StringComparison.Ordinal))
			{
				zipUri = uri;
			}
			else if (string.Equals(name, checksumName, StringComparison.Ordinal))
			{
				checksumUri = uri;
			}
		}

		if (zipUri == null || checksumUri == null)
		{
			throw new InvalidDataException(
				"The latest release does not contain " + zipName + " and its SHA-256 file.");
		}
		return new UpdateRelease(version, zipName, zipUri, checksumUri);
	}

	private static bool IsTrustedDownloadUri(Uri uri)
	{
		return string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
			&& (string.Equals(uri.Host, "github.com", StringComparison.OrdinalIgnoreCase)
				|| uri.Host.EndsWith(".github.com", StringComparison.OrdinalIgnoreCase));
	}

	internal static TimeoutWebClient CreateClient(int timeoutMillis)
	{
		TimeoutWebClient client = new TimeoutWebClient(timeoutMillis);
		client.Headers[HttpRequestHeader.UserAgent] = "HapticScape-Updater";
		client.Headers[HttpRequestHeader.Accept] = "application/vnd.github+json";
		return client;
	}
}

internal sealed class PreparedUpdate
{
	internal string StagedApplicationDirectory { get; private set; }
	internal string TemporaryUpdaterPath { get; private set; }
	internal string TemporaryRoot { get; private set; }

	internal PreparedUpdate(
		string stagedApplicationDirectory,
		string temporaryUpdaterPath,
		string temporaryRoot)
	{
		StagedApplicationDirectory = stagedApplicationDirectory;
		TemporaryUpdaterPath = temporaryUpdaterPath;
		TemporaryRoot = temporaryRoot;
	}
}

internal static class UpdatePackagePreparer
{
	internal static PreparedUpdate Prepare(
		UpdateRelease release,
		ReleaseManifest installedManifest,
		string applicationDirectory,
		Action<string> reportProgress)
	{
		string installedPath = Path.GetFullPath(applicationDirectory)
			.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
		DirectoryInfo parent = Directory.GetParent(installedPath);
		if (parent == null)
		{
			throw new InvalidOperationException("The HapticScape directory has no safe parent directory.");
		}
		string temporaryRoot = Path.Combine(
			parent.FullName,
			"HapticScape-update-" + Guid.NewGuid().ToString("N"));
		Directory.CreateDirectory(temporaryRoot);
		try
		{
			string zipPath = Path.Combine(temporaryRoot, release.ZipName);
			string checksumPath = zipPath + ".sha256";
			Report(reportProgress, "Downloading checksum...");
			using (TimeoutWebClient client = GitHubReleaseClient.CreateClient(UpdateConstants.DownloadTimeoutMillis))
			{
				client.DownloadFile(release.ChecksumUri, checksumPath);
				Report(reportProgress, "Downloading HapticScape " + release.Version + "...");
				client.DownloadFile(release.ZipUri, zipPath);
			}

			FileInfo zipInfo = new FileInfo(zipPath);
			if (!zipInfo.Exists || zipInfo.Length == 0 || zipInfo.Length > UpdateConstants.MaximumZipBytes)
			{
				throw new InvalidDataException("The downloaded update has an invalid size.");
			}

			Report(reportProgress, "Verifying download...");
			VerifySha256(zipPath, File.ReadAllText(checksumPath));

			string extractionRoot = Path.Combine(temporaryRoot, "extracted");
			Report(reportProgress, "Preparing update...");
			ExtractSafely(zipPath, extractionRoot);
			string stagedApplication = Path.Combine(extractionRoot, "HapticScape");
			ValidateStagedApplication(stagedApplication, release, installedManifest.Architecture);

			string stagedUpdater = Path.Combine(
				stagedApplication,
				"app",
				"HapticScapeUpdater.exe");
			string temporaryUpdater = Path.Combine(
				Path.GetTempPath(),
				"HapticScapeUpdater-" + Guid.NewGuid().ToString("N") + ".exe");
			File.Copy(stagedUpdater, temporaryUpdater, false);
			return new PreparedUpdate(stagedApplication, temporaryUpdater, temporaryRoot);
		}
		catch
		{
			TryDeleteDirectory(temporaryRoot);
			throw;
		}
	}

	internal static void VerifySha256(string filePath, string checksumText)
	{
		string[] parts = checksumText.Trim().Split((char[]) null, StringSplitOptions.RemoveEmptyEntries);
		if (parts.Length == 0 || parts[0].Length != 64 || !IsHex(parts[0]))
		{
			throw new InvalidDataException("The release checksum file is malformed.");
		}

		string actual;
		using (SHA256 sha256 = SHA256.Create())
		using (FileStream stream = File.OpenRead(filePath))
		{
			actual = BitConverter.ToString(sha256.ComputeHash(stream)).Replace("-", "");
		}
		if (!string.Equals(actual, parts[0], StringComparison.OrdinalIgnoreCase))
		{
			throw new InvalidDataException("The downloaded update failed SHA-256 verification.");
		}
	}

	internal static void ExtractSafely(string zipPath, string extractionRoot)
	{
		Directory.CreateDirectory(extractionRoot);
		string canonicalRoot = Path.GetFullPath(extractionRoot)
			.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
			+ Path.DirectorySeparatorChar;
		long expandedBytes = 0;
		int entries = 0;

		using (ZipArchive archive = ZipFile.OpenRead(zipPath))
		{
			foreach (ZipArchiveEntry entry in archive.Entries)
			{
				entries++;
				if (entries > UpdateConstants.MaximumArchiveEntries)
				{
					throw new InvalidDataException("The update archive contains too many files.");
				}
				expandedBytes += entry.Length;
				if (expandedBytes > UpdateConstants.MaximumExpandedBytes)
				{
					throw new InvalidDataException("The expanded update is unexpectedly large.");
				}

				string destination = Path.GetFullPath(Path.Combine(extractionRoot, entry.FullName));
				if (!destination.StartsWith(canonicalRoot, StringComparison.OrdinalIgnoreCase))
				{
					throw new InvalidDataException("The update archive contains an unsafe path.");
				}

				if (string.IsNullOrEmpty(entry.Name))
				{
					Directory.CreateDirectory(destination);
					continue;
				}
				string destinationDirectory = Path.GetDirectoryName(destination);
				if (!string.IsNullOrEmpty(destinationDirectory))
				{
					Directory.CreateDirectory(destinationDirectory);
				}
				entry.ExtractToFile(destination, false);
			}
		}
	}

	private static void ValidateStagedApplication(
		string stagedApplication,
		UpdateRelease release,
		string expectedArchitecture)
	{
		string launcher = Path.Combine(stagedApplication, "HapticScape.exe");
		string jar = Path.Combine(stagedApplication, "app", "hapticscape-client.jar");
		string updater = Path.Combine(stagedApplication, "app", "HapticScapeUpdater.exe");
		string manifestPath = Path.Combine(stagedApplication, "app", "release.json");
		if (!File.Exists(launcher) || !File.Exists(jar) || !File.Exists(updater))
		{
			throw new InvalidDataException("The update package is missing required HapticScape files.");
		}
		ReleaseManifest manifest = ReleaseManifest.Load(manifestPath);
		if (!string.Equals(manifest.Version, release.Version, StringComparison.Ordinal)
			|| !string.Equals(manifest.Architecture, expectedArchitecture, StringComparison.OrdinalIgnoreCase))
		{
			throw new InvalidDataException("The update package metadata does not match the selected release.");
		}
	}

	private static bool IsHex(string value)
	{
		foreach (char character in value)
		{
			if (!Uri.IsHexDigit(character))
			{
				return false;
			}
		}
		return true;
	}

	private static void Report(Action<string> reporter, string message)
	{
		if (reporter != null)
		{
			reporter(message);
		}
	}

	internal static void TryDeleteDirectory(string directory)
	{
		try
		{
			if (Directory.Exists(directory))
			{
				Directory.Delete(directory, true);
			}
		}
		catch (Exception)
		{
			// Temporary cleanup is best-effort.
		}
	}
}

internal static class VersionUtility
{
	internal static bool TryParseStable(string value, out Version version)
	{
		version = null;
		if (string.IsNullOrWhiteSpace(value))
		{
			return false;
		}
		string normalized = WithoutPrefix(value.Trim());
		if (normalized.IndexOf('-') >= 0 || normalized.IndexOf('+') >= 0)
		{
			return false;
		}
		return Version.TryParse(normalized, out version);
	}

	internal static string WithoutPrefix(string value)
	{
		return value.StartsWith("v", StringComparison.OrdinalIgnoreCase)
			? value.Substring(1)
			: value;
	}

	internal static bool IsNewer(string candidate, string installed)
	{
		Version candidateVersion;
		Version installedVersion;
		return TryParseStable(candidate, out candidateVersion)
			&& TryParseStable(installed, out installedVersion)
			&& candidateVersion.CompareTo(installedVersion) > 0;
	}
}

internal static class UpdatePolicy
{
	internal static bool ShouldCheck(UpdatePreferences preferences, DateTime utcNow)
	{
		if (preferences.ForceCheck)
		{
			return true;
		}
		if (!preferences.AutomaticUpdates && !preferences.UpdateNotifications)
		{
			return false;
		}
		return !preferences.LastCheckUtc.HasValue
			|| utcNow - preferences.LastCheckUtc.Value >= UpdateConstants.CheckInterval;
	}
}

internal static class UpdateConstants
{
	internal const string Repository = "ashy0019/HapticScape";
	internal const int CheckTimeoutMillis = 4000;
	internal const int DownloadTimeoutMillis = 120000;
	internal const long MaximumZipBytes = 500L * 1024L * 1024L;
	internal const long MaximumExpandedBytes = 1024L * 1024L * 1024L;
	internal const int MaximumArchiveEntries = 10000;
	internal static readonly TimeSpan CheckInterval = TimeSpan.FromHours(4);
}

internal sealed class TimeoutWebClient : WebClient
{
	private readonly int timeoutMillis;

	internal TimeoutWebClient(int timeoutMillis)
	{
		this.timeoutMillis = timeoutMillis;
	}

	protected override WebRequest GetWebRequest(Uri address)
	{
		WebRequest request = base.GetWebRequest(address);
		request.Timeout = timeoutMillis;
		HttpWebRequest httpRequest = request as HttpWebRequest;
		if (httpRequest != null)
		{
			httpRequest.ReadWriteTimeout = timeoutMillis;
		}
		return request;
	}
}

internal static class JsonValues
{
	internal static Dictionary<string, object> ParseObject(string json)
	{
		Dictionary<string, object> values =
			new JavaScriptSerializer().DeserializeObject(json) as Dictionary<string, object>;
		if (values == null)
		{
			throw new InvalidDataException("JSON data is malformed.");
		}
		return values;
	}

	internal static string RequiredString(Dictionary<string, object> values, string key)
	{
		string value = OptionalString(values, key);
		if (string.IsNullOrWhiteSpace(value))
		{
			throw new InvalidDataException("JSON data is missing " + key + ".");
		}
		return value;
	}

	internal static string OptionalString(Dictionary<string, object> values, string key)
	{
		object value;
		return values.TryGetValue(key, out value) && value != null
			? Convert.ToString(value, CultureInfo.InvariantCulture)
			: null;
	}

	internal static bool OptionalBoolean(
		Dictionary<string, object> values,
		string key,
		bool defaultValue)
	{
		object value;
		if (!values.TryGetValue(key, out value) || value == null)
		{
			return defaultValue;
		}
		bool parsed;
		return bool.TryParse(Convert.ToString(value, CultureInfo.InvariantCulture), out parsed)
			? parsed
			: defaultValue;
	}
}
