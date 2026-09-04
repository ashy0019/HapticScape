package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SettingsLockServiceTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void storesOnlyVerifierAndSurvivesRestart() throws Exception
	{
		Gson gson = new Gson();
		Path path = temporaryFolder.getRoot().toPath().resolve("settings-lock.json");
		char[] password = "controller-only password".toCharArray();
		try
		{
			SettingsLockService original = new SettingsLockService(gson, path);
			original.arm(original.createProposal(password));
			assertTrue(original.isLocked());

			String persisted = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			assertFalse(persisted.contains(new String(password)));

			SettingsLockService restarted = new SettingsLockService(gson, path);
			assertTrue(restarted.isLocked());
			assertFalse(restarted.unlock("definitely incorrect".toCharArray()));
			assertTrue(restarted.isLocked());
			assertTrue(restarted.unlock(password));
			assertFalse(restarted.isLocked());
			assertFalse(Files.exists(path));
		}
		finally
		{
			Arrays.fill(password, '\0');
		}
	}

	@Test(expected = IllegalStateException.class)
	public void existingLockCannotBeSilentlyReplaced()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("replacement-lock.json")
		);
		service.arm(service.createProposal("original password".toCharArray()));
		service.arm(service.createProposal("replacement password".toCharArray()));
	}

	@Test
	public void developerRecoveryClearsLockWithoutPassword()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("recovery-lock.json")
		);
		service.arm(service.createProposal("forgotten password".toCharArray()));
		assertTrue(service.isLocked());

		service.clearAllLocks();

		assertFalse(service.isLocked());
	}

	@Test
	public void generatedUnlockKeysAreReadableStrongAndUsable()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("generated-key-lock.json")
		);
		char[] first = service.generateUnlockKey();
		char[] second = service.generateUnlockKey();
		try
		{
			String firstText = new String(first);
			String secondText = new String(second);
			assertTrue(firstText.matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){4}"));
			assertNotEquals(firstText, secondText);

			service.arm(service.createProposal(first));
			assertTrue(service.unlock(first));
			assertFalse(service.isLocked());
		}
		finally
		{
			Arrays.fill(first, '\0');
			Arrays.fill(second, '\0');
		}
	}

	@Test
	public void lockLeavesForgeAndEveryMusicControlEditable()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("local-exceptions-lock.json")
		);
		service.arm(service.createProposal("policy test password".toCharArray()));

		assertTrue(service.canEditLocally(HapticScapeConfig.CUSTOM_PATTERNS_KEY));
		assertTrue(service.canEditLocally(HapticScapeConfig.MUSIC_SYNC_ENABLED_KEY));
		assertTrue(service.canEditLocally(HapticScapeConfig.MUSIC_RESPONSE_KEY));
		assertTrue(service.canEditLocally(HapticScapeConfig.MUSIC_SENSITIVITY_PERCENT_KEY));
		assertTrue(service.canEditLocally(
			HapticScapeConfig.MUSIC_MINIMUM_INTENSITY_PERCENT_KEY
		));
		assertTrue(service.canEditLocally(
			HapticScapeConfig.MUSIC_MAXIMUM_INTENSITY_PERCENT_KEY
		));
		assertFalse(service.canEditLocally(HapticScapeConfig.INTENSITY_PERCENT_KEY));
		assertFalse(service.canEditLocally(HapticScapeConfig.CLICKER_ENABLED_KEY));
	}
}
