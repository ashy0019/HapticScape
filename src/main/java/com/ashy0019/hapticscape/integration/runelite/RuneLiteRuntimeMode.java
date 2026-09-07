package com.ashy0019.hapticscape.integration.runelite;

/** Transitional host-mode switch used while HapticScape moves into its own process. */
public final class RuneLiteRuntimeMode
{
	public static final String EXTERNAL_RUNTIME_PROPERTY = "hapticscape.externalRuntime";

	private RuneLiteRuntimeMode()
	{
	}

	public static boolean usesExternalRuntime()
	{
		return Boolean.parseBoolean(
			System.getProperty(EXTERNAL_RUNTIME_PROPERTY, "false")
		);
	}
}
