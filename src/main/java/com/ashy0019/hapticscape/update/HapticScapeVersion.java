package com.ashy0019.hapticscape.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class HapticScapeVersion
{
	private static final String CURRENT = load();

	private HapticScapeVersion()
	{
	}

	public static String current()
	{
		return CURRENT;
	}

	private static String load()
	{
		try (InputStream stream = HapticScapeVersion.class
			.getResourceAsStream("/hapticscape-version.properties"))
		{
			if (stream == null)
			{
				return "development";
			}
			Properties properties = new Properties();
			properties.load(stream);
			return properties.getProperty("version", "development");
		}
		catch (IOException exception)
		{
			return "development";
		}
	}
}
