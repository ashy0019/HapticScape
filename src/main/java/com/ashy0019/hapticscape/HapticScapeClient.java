package com.ashy0019.hapticscape;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with HapticScape registered as a built-in plugin.
 *
 * <p>This entry point belongs to the distributable client. Keeping it in the
 * main source set prevents the Windows package from needing to include test
 * classes or test-framework dependencies.</p>
 */
public final class HapticScapeClient
{
	private HapticScapeClient()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HapticScapePlugin.class);
		RuneLite.main(args);
	}
}
