package com.ashy0019.hapticscape;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HapticScapePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HapticScapePlugin.class);
		RuneLite.main(args);
	}
}
