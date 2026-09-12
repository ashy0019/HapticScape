package com.ashy0019.localeventbridge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LocalEventBridgePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LocalEventBridgePlugin.class);
		RuneLite.main(args);
	}
}
