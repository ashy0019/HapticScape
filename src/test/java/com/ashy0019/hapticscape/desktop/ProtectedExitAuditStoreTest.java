package com.ashy0019.hapticscape.desktop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
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
}
