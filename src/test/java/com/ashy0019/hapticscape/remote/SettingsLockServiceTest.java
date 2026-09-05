package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
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

	@Test
	public void disjointTargetLocksKeepIndependentKeysAcrossRestart() throws Exception
	{
		Gson gson = new Gson();
		Path path = temporaryFolder.getRoot().toPath().resolve("atomic-locks.json");
		char[] levelUpKey = "level up lock password".toCharArray();
		char[] clickerKey = "clicker lock password".toCharArray();
		try
		{
			SettingsLockService service = new SettingsLockService(gson, path);
			service.arm(service.createProposal(
				levelUpKey,
				Collections.singleton(SettingsLockCatalog.LEVEL_UP_HAPTICS)
			));
			service.arm(service.createProposal(
				clickerKey,
				Collections.singleton(SettingsLockCatalog.CLICKER_ENABLED)
			));

			assertTrue(service.isLocked(SettingsLockCatalog.LEVEL_UP_HAPTICS));
			assertTrue(service.isLocked(SettingsLockCatalog.CLICKER_ENABLED));
			assertFalse(service.isLocked(SettingsLockCatalog.MILESTONE_HAPTICS));
			assertFalse(service.canEditLocally(
				SettingsLockCatalog.LEVEL_UP_HAPTICS,
				HapticScapeConfig.LEVEL_UP_FEEDBACK_ENABLED_KEY
			));
			assertTrue(service.canEditLocally(
				SettingsLockCatalog.MILESTONE_HAPTICS,
				HapticScapeConfig.MILESTONE_FEEDBACK_ENABLED_KEY
			));
			assertEquals(2, service.getSnapshot().getLockCount());

			assertTrue(service.unlock(levelUpKey));
			assertFalse(service.isLocked(SettingsLockCatalog.LEVEL_UP_HAPTICS));
			assertTrue(service.isLocked(SettingsLockCatalog.CLICKER_ENABLED));

			SettingsLockService restarted = new SettingsLockService(gson, path);
			assertFalse(restarted.isLocked(SettingsLockCatalog.LEVEL_UP_HAPTICS));
			assertTrue(restarted.isLocked(SettingsLockCatalog.CLICKER_ENABLED));
			assertTrue(restarted.unlock(clickerKey));
			assertFalse(restarted.isLocked());
		}
		finally
		{
			Arrays.fill(levelUpKey, '\0');
			Arrays.fill(clickerKey, '\0');
		}
	}

	@Test(expected = IllegalStateException.class)
	public void overlappingTargetProposalIsRejectedAtomically()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("overlap-lock.json")
		);
		service.arm(service.createProposal(
			"first target password".toCharArray(),
			Collections.singleton(SettingsLockCatalog.LEVEL_UP_HAPTICS)
		));
		service.arm(service.createProposal(
			"second target password".toCharArray(),
			Collections.singleton(SettingsLockCatalog.LEVEL_UP_HAPTICS)
		));
	}

	@Test
	public void legacySingleProposalFileStillLoadsAsFullLock() throws Exception
	{
		Gson gson = new Gson();
		Path path = temporaryFolder.getRoot().toPath().resolve("legacy-lock.json");
		char[] password = "legacy lock password".toCharArray();
		try
		{
			SettingsLockProposal legacy = SettingsLockProposal.create(password);
			Files.write(path, gson.toJson(legacy).getBytes(StandardCharsets.UTF_8));

			SettingsLockService service = new SettingsLockService(gson, path);
			assertTrue(service.getSnapshot().isLegacyFullLock());
			assertTrue(service.isLocked(SettingsLockCatalog.LEVEL_UP_HAPTICS));
			assertFalse(service.canEditLocally(HapticScapeConfig.CLICKER_ENABLED_KEY));
			assertTrue(service.unlock(password));
		}
		finally
		{
			Arrays.fill(password, '\0');
		}
	}
}
