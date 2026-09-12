package com.ashy0019.hapticscape.bridgeclient;

import com.ashy0019.localeventbridge.LocalEventBridgePlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Minimal packaged RuneLite entry point used when the Local Event Bridge is
 * not installed through the RuneLite Plugin Hub.
 */
public final class HapticScapeBridgeClient
{
    private HapticScapeBridgeClient()
    {
    }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(LocalEventBridgePlugin.class);
        RuneLite.main(args);
    }
}
