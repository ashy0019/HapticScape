package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import net.runelite.client.RuneLite;

/** Provides legacy-compatible HapticScape storage locations for the RuneLite host. */
public final class RuneLiteStoragePaths
{
	private RuneLiteStoragePaths()
	{
	}

	public static HapticScapeStoragePaths create()
	{
		return new HapticScapeStoragePaths(
			RuneLite.RUNELITE_DIR.toPath().resolve("hapticscape")
		);
	}
}
