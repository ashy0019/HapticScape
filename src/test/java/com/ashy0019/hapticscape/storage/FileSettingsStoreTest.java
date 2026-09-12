package com.ashy0019.hapticscape.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Test;

public class FileSettingsStoreTest
{
	@Test
	public void createsParentDirectoryAndPersistsValuesAcrossInstances() throws Exception
	{
		Path root = Files.createTempDirectory("hapticscape-file-settings");
		Path settingsPath = root.resolve("nested").resolve("settings.properties");

		FileSettingsStore first = new FileSettingsStore(settingsPath);
		assertNull(first.get("intensityPercent"));
		first.set("intensityPercent", 73);
		first.set("clickerEnabled", true);

		assertTrue(Files.isRegularFile(settingsPath));

		FileSettingsStore second = new FileSettingsStore(settingsPath);
		assertEquals("73", second.get("intensityPercent"));
		assertEquals("true", second.get("clickerEnabled"));
	}

	@Test
	public void preservesStringValuesThatNeedPropertiesEscaping() throws Exception
	{
		Path settingsPath = Files.createTempDirectory("hapticscape-file-settings")
			.resolve("settings.properties");
		String value = "wss://relay.example/path?a=b:c\\d\nnext";

		new FileSettingsStore(settingsPath).set("remoteRelayUrl", value);

		assertEquals(value, new FileSettingsStore(settingsPath).get("remoteRelayUrl"));
	}

	@Test
	public void replacingAValueDoesNotLeaveTemporaryFilesBehind() throws Exception
	{
		Path root = Files.createTempDirectory("hapticscape-file-settings");
		Path settingsPath = root.resolve("settings.properties");
		FileSettingsStore store = new FileSettingsStore(settingsPath);

		store.set("rogueCoins", 10);
		store.set("rogueCoins", 25);

		assertEquals("25", new FileSettingsStore(settingsPath).get("rogueCoins"));
		try (Stream<Path> entries = Files.list(root))
		{
			assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
		}
	}

	@Test
	public void writesUtf8PropertiesFile() throws Exception
	{
		Path settingsPath = Files.createTempDirectory("hapticscape-file-settings")
			.resolve("settings.properties");
		new FileSettingsStore(settingsPath).set("example", "caf\u00e9");

		String file = new String(Files.readAllBytes(settingsPath), StandardCharsets.UTF_8);
		assertTrue(file.contains("example=caf\u00e9"));
	}
}
