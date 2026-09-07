package com.ashy0019.hapticscape;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with the minimal HapticScape gameplay bridge registered as a
 * built-in plugin. The standalone HapticScape application must already be
 * listening on the local gameplay transport.
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
