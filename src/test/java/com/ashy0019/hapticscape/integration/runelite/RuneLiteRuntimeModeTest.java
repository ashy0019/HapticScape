package com.ashy0019.hapticscape.integration.runelite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuneLiteRuntimeModeTest
{
	@Test
	public void defaultsToEmbeddedRuntime()
	{
		withProperty(null, () -> assertFalse(RuneLiteRuntimeMode.usesExternalRuntime()));
	}

	@Test
	public void enablesExternalRuntimeFromSystemProperty()
	{
		withProperty("true", () -> assertTrue(RuneLiteRuntimeMode.usesExternalRuntime()));
	}

	private static void withProperty(String value, Runnable assertion)
	{
		String key = RuneLiteRuntimeMode.EXTERNAL_RUNTIME_PROPERTY;
		String previous = System.getProperty(key);
		try
		{
			if (value == null)
			{
				System.clearProperty(key);
			}
			else
			{
				System.setProperty(key, value);
			}
			assertion.run();
		}
		finally
		{
			if (previous == null)
			{
				System.clearProperty(key);
			}
			else
			{
				System.setProperty(key, previous);
			}
		}
	}
}
