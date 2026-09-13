package com.ashy0019.hapticscape.desktop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProtectedExitAuditStoreTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void unclearedRunBecomesPendingUnauthorizedEnd()
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("protected-exit.properties");
		ProtectedExitAuditStore first = new ProtectedExitAuditStore(path);
		first.beginRun(true);

		ProtectedExitAuditStore restarted = new ProtectedExitAuditStore(path);
		restarted.beginRun(true);

		assertTrue(restarted.hasPendingUnauthorizedEnd());
	}

	@Test
	public void authorizedEndDoesNotCreateAFlagOnRestart()
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("protected-exit.properties");
		ProtectedExitAuditStore first = new ProtectedExitAuditStore(path);
		first.beginRun(true);
		first.markAuthorizedEnd();

		ProtectedExitAuditStore restarted = new ProtectedExitAuditStore(path);
		restarted.beginRun(true);

		assertFalse(restarted.hasPendingUnauthorizedEnd());
	}

	@Test
	public void explicitUnauthorizedEndPersistsUntilReported()
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("protected-exit.properties");
		ProtectedExitAuditStore store = new ProtectedExitAuditStore(path);
		store.beginRun(true);
		store.markUnauthorizedEnd();

		ProtectedExitAuditStore restarted = new ProtectedExitAuditStore(path);
		assertTrue(restarted.hasPendingUnauthorizedEnd());
		restarted.clearPendingUnauthorizedEnd();
		assertFalse(new ProtectedExitAuditStore(path).hasPendingUnauthorizedEnd());
	}

	@Test
	public void unprotectedCrashDoesNotCreateAFlag()
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("protected-exit.properties");
		new ProtectedExitAuditStore(path).beginRun(false);

		ProtectedExitAuditStore restarted = new ProtectedExitAuditStore(path);
		restarted.beginRun(false);

		assertFalse(restarted.hasPendingUnauthorizedEnd());
	}

	@Test
	public void pendingEventIsControllerBoundAndClearsOnlyAfterMatchingAck()
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("protected-exit.properties");
		String controllerId = UUID.randomUUID().toString();
		ProtectedExitAuditStore store = new ProtectedExitAuditStore(path);
		store.beginRun(true, controllerId);
		ProtectedExitAuditStore.UnauthorizedEndRecord record =
			store.markUnauthorizedEnd(UUID.randomUUID().toString());

		assertTrue(store.hasPendingUnauthorizedEnd());
		assertTrue(controllerId.equals(record.getControllerId()));
		assertFalse(store.clearPendingUnauthorizedEnd(UUID.randomUUID().toString()));
		assertTrue(store.hasPendingUnauthorizedEnd());
		assertTrue(store.clearPendingUnauthorizedEnd(record.getEventId()));
		assertFalse(new ProtectedExitAuditStore(path).hasPendingUnauthorizedEnd());
	}

	@Test
	public void legacyBooleanFlagMigratesToCurrentLockOwner() throws Exception
	{
		Path path = temporaryFolder.getRoot().toPath().resolve("protected-exit.properties");
		Files.write(path, ("unauthorizedEndPending=true\n"
			+ "running=false\nprotected=false\n").getBytes(StandardCharsets.UTF_8));
		String controllerId = UUID.randomUUID().toString();

		ProtectedExitAuditStore migrated = new ProtectedExitAuditStore(path);
		migrated.beginRun(true, controllerId);

		ProtectedExitAuditStore.UnauthorizedEndRecord record = migrated
			.getPendingUnauthorizedEnd().orElseThrow(AssertionError::new);
		assertTrue(controllerId.equals(record.getControllerId()));
	}
}
