package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeSettingKeys;

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
	public void generatedUnlockKeysAcceptHumanFriendlyFormatting()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("formatted-key-lock.json")
		);
		char[] generated = service.generateUnlockKey();
		try
		{
			service.arm(service.createProposal(generated));
			String entered = new String(generated).toLowerCase().replace('-', ' ');
			assertTrue(service.unlock(entered.toCharArray()));
		}
		finally
		{
			Arrays.fill(generated, '\0');
		}
	}

	@Test
	public void customPasswordsRemainCaseSensitive()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("case-sensitive-lock.json")
		);
		service.arm(service.createProposal("My custom Password".toCharArray()));
		assertFalse(service.unlock("my custom password".toCharArray()));
		assertTrue(service.unlock("My custom Password".toCharArray()));
	}

	@Test
	public void lockLeavesForgeAndEveryMusicControlEditable()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("local-exceptions-lock.json")
		);
		service.arm(service.createProposal("policy test password".toCharArray()));

		assertTrue(service.canEditLocally(HapticScapeSettingKeys.CUSTOM_PATTERNS));
		assertTrue(service.canEditLocally(HapticScapeSettingKeys.MUSIC_SYNC_ENABLED));
		assertTrue(service.canEditLocally(HapticScapeSettingKeys.MUSIC_RESPONSE));
		assertTrue(service.canEditLocally(HapticScapeSettingKeys.MUSIC_SENSITIVITY_PERCENT));
		assertTrue(service.canEditLocally(
			HapticScapeSettingKeys.MUSIC_MINIMUM_INTENSITY_PERCENT
		));
		assertTrue(service.canEditLocally(
			HapticScapeSettingKeys.MUSIC_MAXIMUM_INTENSITY_PERCENT
		));
		assertFalse(service.canEditLocally(HapticScapeSettingKeys.INTENSITY_PERCENT));
		assertTrue(service.canEditLocally(HapticScapeSettingKeys.CLICKER_ENABLED));
		assertTrue(service.canEditLocally(HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT));
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
				HapticScapeSettingKeys.LEVEL_UP_FEEDBACK_ENABLED
			));
			assertTrue(service.canEditLocally(
				SettingsLockCatalog.MILESTONE_HAPTICS,
				HapticScapeSettingKeys.MILESTONE_FEEDBACK_ENABLED
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
	public void sectionLockCoversEveryRegisteredChild()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("section-lock.json")
		);
		service.arm(service.createProposal(
			"feedback section password".toCharArray(),
			Collections.singleton(SettingsLockCatalog.FEEDBACK_BLOCK)
		));

		assertTrue(service.isLocked(SettingsLockCatalog.FEEDBACK_BLOCK));
		assertTrue(service.isLocked(SettingsLockCatalog.LEVEL_UP_HAPTICS));
		assertTrue(service.isLocked(SettingsLockCatalog.MILESTONE_HAPTICS));
		assertTrue(service.isLocked(SettingsLockCatalog.LEVEL_99_HAPTICS));
		assertFalse(service.isLocked(SettingsLockCatalog.CLICKER_ENABLED));
	}

	@Test
	public void protectedActionAuthorizationDoesNotRemoveTheLock()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("protected-exit-lock.json")
		);
		char[] password = "protected exit password".toCharArray();
		service.arm(service.createProposal(
			password,
			Collections.singleton(SettingsLockCatalog.PROTECTED_EXIT)
		));

		assertFalse(service.authorizes(
			SettingsLockCatalog.PROTECTED_EXIT,
			"wrong password".toCharArray()
		));
		assertTrue(service.authorizes(SettingsLockCatalog.PROTECTED_EXIT, password));
		assertTrue(service.isLocked(SettingsLockCatalog.PROTECTED_EXIT));
	}

	@Test
	public void targetedUnlockReleasesTheWholeProtectedExitProfile()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("protected-exit-profile.json")
		);
		char[] password = "protected exit profile password".toCharArray();
		service.arm(service.createProposal(
			password,
			Arrays.asList(
				SettingsLockCatalog.PROTECTED_EXIT,
				SettingsLockCatalog.LEVEL_UP_HAPTICS
			)
		));

		assertFalse(service.unlock(SettingsLockCatalog.STARTUP_BEHAVIOR, password));
		assertTrue(service.isLocked(SettingsLockCatalog.PROTECTED_EXIT));
		assertTrue(service.unlock(SettingsLockCatalog.PROTECTED_EXIT, password));
		assertFalse(service.isLocked());
		assertFalse(service.isLocked(SettingsLockCatalog.LEVEL_UP_HAPTICS));
	}

	@Test(expected = IllegalStateException.class)
	public void childLockPreventsOverlappingSectionLock()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("child-before-section.json")
		);
		service.arm(service.createProposal(
			"level up child password".toCharArray(),
			Collections.singleton(SettingsLockCatalog.LEVEL_UP_HAPTICS)
		));
		service.arm(service.createProposal(
			"feedback section password".toCharArray(),
			Collections.singleton(SettingsLockCatalog.FEEDBACK_BLOCK)
		));
	}

	@Test(expected = IllegalStateException.class)
	public void sectionLockPreventsOverlappingChildLock()
	{
		SettingsLockService service = new SettingsLockService(
			new Gson(),
			temporaryFolder.getRoot().toPath().resolve("section-before-child.json")
		);
		service.arm(service.createProposal(
			"feedback section password".toCharArray(),
			Collections.singleton(SettingsLockCatalog.FEEDBACK_BLOCK)
		));
		service.arm(service.createProposal(
			"level up child password".toCharArray(),
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
			assertFalse(service.isLocked(SettingsLockCatalog.PROTECTED_EXIT));
			assertTrue(service.canEditLocally(HapticScapeSettingKeys.CLICKER_ENABLED));
			assertTrue(service.canEditLocally(HapticScapeSettingKeys.CLICKER_VOLUME_PERCENT));
			assertTrue(service.unlock(password));
		}
		finally
		{
			Arrays.fill(password, '\0');
		}
	}
}
