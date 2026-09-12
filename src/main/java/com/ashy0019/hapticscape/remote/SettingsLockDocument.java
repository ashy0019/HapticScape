package com.ashy0019.hapticscape.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned on-disk collection of independent, non-overlapping lock bundles. */
final class SettingsLockDocument
{
	static final int LEGACY_DOCUMENT_SCHEMA_VERSION = 2;
	static final int SCHEMA_VERSION = 3;

	private final int schemaVersion;
	private final List<SettingsLockProposal> locks;

	SettingsLockDocument(List<SettingsLockProposal> locks)
	{
		this.schemaVersion = SCHEMA_VERSION;
		this.locks = Collections.unmodifiableList(new ArrayList<>(locks));
	}

	List<SettingsLockProposal> validateAndGetLocks()
	{
		if ((schemaVersion != LEGACY_DOCUMENT_SCHEMA_VERSION && schemaVersion != SCHEMA_VERSION) || locks == null)
		{
			throw new IllegalArgumentException("Unsupported settings-lock document");
		}
		if (locks.size() > 128)
		{
			throw new IllegalArgumentException("Too many settings locks");
		}
		Set<String> lockIds = new HashSet<>();
		Set<String> ownerIds = new HashSet<>();
		Set<SettingsLockTarget> targets = new HashSet<>();
		boolean legacyFullLock = false;
		for (SettingsLockProposal lock : locks)
		{
			if (lock == null)
			{
				throw new IllegalArgumentException("Settings-lock document contains a null lock");
			}
			lock.validate();
			if (!lockIds.add(lock.getProposalId()))
			{
				throw new IllegalArgumentException("Duplicate settings-lock ID");
			}
			if (lock.isNamedProfile() && !ownerIds.add(lock.getOwnerId()))
			{
				throw new IllegalArgumentException("Duplicate controller-owned settings-lock profile");
			}
			if (legacyFullLock || lock.isLegacyFullLock())
			{
				if (legacyFullLock || locks.size() > 1)
				{
					throw new IllegalArgumentException(
						"A legacy full lock cannot overlap another settings lock"
					);
				}
				legacyFullLock = true;
				continue;
			}
			for (SettingsLockTarget target : lock.getTargets())
			{
				for (SettingsLockTarget existing : targets)
				{
					if (SettingsLockCatalog.conflicts(target, existing))
					{
						throw new IllegalArgumentException(
							"Overlapping settings-lock target: " + target.getId()
						);
					}
				}
				targets.add(target);
			}
		}
		return Collections.unmodifiableList(new ArrayList<>(locks));
	}
}
