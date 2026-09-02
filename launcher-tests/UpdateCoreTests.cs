using System;
using System.IO;
using System.IO.Compression;

internal static class UpdateCoreTests
{
	private static int assertions;

	private static int Main()
	{
		string root = Path.Combine(Path.GetTempPath(), "HapticScape-tests-" + Guid.NewGuid().ToString("N"));
		Directory.CreateDirectory(root);
		try
		{
			TestVersions();
			TestPolicy();
			TestReleaseParsing();
			TestPreferences(root);
			TestChecksum(root);
			TestSafeExtraction(root);
			TestTraversalRejection(root);
			Console.WriteLine("HapticScape updater tests passed: " + assertions);
			return 0;
		}
		catch (Exception exception)
		{
			Console.Error.WriteLine(exception);
			return 1;
		}
		finally
		{
			UpdatePackagePreparer.TryDeleteDirectory(root);
		}
	}

	private static void TestVersions()
	{
		Assert(VersionUtility.IsNewer("1.6.1", "1.6.0"), "patch version should be newer");
		Assert(VersionUtility.IsNewer("v2.0.0", "1.99.0"), "v prefix should be accepted");
		Assert(!VersionUtility.IsNewer("1.6.0", "1.6.0"), "equal versions are not newer");
		Version ignored;
		Assert(!VersionUtility.TryParseStable("1.7.0-beta", out ignored), "prereleases are rejected");
	}

	private static void TestPolicy()
	{
		DateTime now = DateTime.UtcNow;
		UpdatePreferences disabled = UpdatePreferences.Defaults();
		disabled.UpdateNotifications = false;
		Assert(!UpdatePolicy.ShouldCheck(disabled, now), "fully disabled updates should not check");

		UpdatePreferences enabled = UpdatePreferences.Defaults();
		Assert(UpdatePolicy.ShouldCheck(enabled, now), "notifications should permit a check");
		enabled.LastCheckUtc = now.Subtract(TimeSpan.FromHours(1));
		Assert(!UpdatePolicy.ShouldCheck(enabled, now), "recent successful checks should be cached");
		enabled.ForceCheck = true;
		Assert(UpdatePolicy.ShouldCheck(enabled, now), "manual check should bypass the cache");
	}

	private static void TestReleaseParsing()
	{
		string json = "{"
			+ "\"tag_name\":\"v1.6.0\",\"draft\":false,\"prerelease\":false,"
			+ "\"assets\":["
			+ "{\"name\":\"HapticScape-Windows-x64-1.6.0.zip\","
			+ "\"browser_download_url\":\"https://github.com/ashy0019/HapticScape/releases/download/v1.6.0/HapticScape-Windows-x64-1.6.0.zip\"},"
			+ "{\"name\":\"HapticScape-Windows-x64-1.6.0.zip.sha256\","
			+ "\"browser_download_url\":\"https://github.com/ashy0019/HapticScape/releases/download/v1.6.0/HapticScape-Windows-x64-1.6.0.zip.sha256\"}]}";
		UpdateRelease release = GitHubReleaseClient.ParseLatest(json, "x64");
		Assert(release.Version == "1.6.0", "release version should be normalized");
		Assert(release.ZipName == "HapticScape-Windows-x64-1.6.0.zip", "asset name should match exactly");
	}

	private static void TestPreferences(string root)
	{
		string path = Path.Combine(root, "preferences", "updater-settings.json");
		UpdatePreferences saved = UpdatePreferences.Defaults();
		saved.AutomaticUpdates = true;
		saved.UpdateNotifications = false;
		saved.SkippedVersion = "1.6.2";
		saved.ForceCheck = true;
		UpdatePreferencesStore.Save(path, saved);
		UpdatePreferences loaded = UpdatePreferencesStore.Load(path);
		Assert(loaded.AutomaticUpdates, "automatic update choice should round-trip");
		Assert(!loaded.UpdateNotifications, "notification choice should round-trip");
		Assert(loaded.SkippedVersion == "1.6.2", "skipped version should round-trip");
		Assert(loaded.ForceCheck, "manual check request should round-trip");
	}

	private static void TestChecksum(string root)
	{
		string path = Path.Combine(root, "checksum.txt");
		File.WriteAllText(path, "abc");
		UpdatePackagePreparer.VerifySha256(
			path,
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad *checksum.txt");
		Assert(true, "known SHA-256 should verify");
		AssertThrows<InvalidDataException>(delegate
		{
			UpdatePackagePreparer.VerifySha256(path, new string('0', 64));
		}, "incorrect SHA-256 should fail");
	}

	private static void TestSafeExtraction(string root)
	{
		string zip = Path.Combine(root, "safe.zip");
		using (ZipArchive archive = ZipFile.Open(zip, ZipArchiveMode.Create))
		{
			ZipArchiveEntry entry = archive.CreateEntry("HapticScape/app/test.txt");
			using (StreamWriter writer = new StreamWriter(entry.Open()))
			{
				writer.Write("safe");
			}
		}
		string output = Path.Combine(root, "safe-output");
		UpdatePackagePreparer.ExtractSafely(zip, output);
		Assert(File.Exists(Path.Combine(output, "HapticScape", "app", "test.txt")),
			"safe archive should extract");
	}

	private static void TestTraversalRejection(string root)
	{
		string zip = Path.Combine(root, "unsafe.zip");
		using (ZipArchive archive = ZipFile.Open(zip, ZipArchiveMode.Create))
		{
			ZipArchiveEntry entry = archive.CreateEntry("../escape.txt");
			using (StreamWriter writer = new StreamWriter(entry.Open()))
			{
				writer.Write("unsafe");
			}
		}
		AssertThrows<InvalidDataException>(delegate
		{
			UpdatePackagePreparer.ExtractSafely(zip, Path.Combine(root, "unsafe-output"));
		}, "path traversal should be rejected");
	}

	private static void Assert(bool condition, string message)
	{
		assertions++;
		if (!condition)
		{
			throw new InvalidOperationException("Assertion failed: " + message);
		}
	}

	private static void AssertThrows<T>(Action action, string message) where T : Exception
	{
		assertions++;
		try
		{
			action();
		}
		catch (T)
		{
			return;
		}
		throw new InvalidOperationException("Assertion failed: " + message);
	}
}
