package com.ashy0019.hapticscape.remote;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiscordCredentialStoreTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void credentialIsProtectedAndSurvivesRestart() throws Exception
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("discord-device.json");
		TestUnlockKeyProtector protector = new TestUnlockKeyProtector();
		String secret = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
		DiscordCredentialStore store = new DiscordCredentialStore(
			new Gson(),
			path,
			protector
		);
		store.save(new DiscordDeviceCredential(
			"123456789012345678",
			"Test User",
			"wss://relay.example/relay",
			secret
		));

		String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		assertFalse(json.contains(secret));
		assertEquals(1, protector.getProtectCount());

		DiscordCredentialStore restarted = new DiscordCredentialStore(
			new Gson(),
			path,
			protector
		);
		DiscordDeviceCredential loaded = restarted.get().get();
		assertEquals("123456789012345678", loaded.getUserId());
		assertEquals("Test User", loaded.getDisplayName());
		assertEquals("wss://relay.example/relay", loaded.getRelayUrl());
		assertEquals(secret, loaded.getSecret());

		restarted.clear();
		assertFalse(Files.exists(path));
		assertFalse(restarted.get().isPresent());
	}

	@Test
	public void damagedCredentialFileIsPreserved() throws Exception
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("discord-device.json");
		Files.write(path, "damaged".getBytes(StandardCharsets.UTF_8));
		DiscordCredentialStore store = new DiscordCredentialStore(
			new Gson(),
			path,
			new TestUnlockKeyProtector()
		);

		assertFalse(store.isAvailable());
		assertTrue(store.getUnavailableMessage().contains("could not read"));
		try
		{
			store.save(new DiscordDeviceCredential(
				"123456789012345678",
				"Test User",
				"wss://relay.example/relay",
				"abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
			));
			fail("Expected the damaged file to block writes");
		}
		catch (IllegalStateException expected)
		{
			assertTrue(expected.getMessage().contains("could not read"));
		}
		assertEquals("damaged", new String(
			Files.readAllBytes(path),
			StandardCharsets.UTF_8
		));
	}
}
