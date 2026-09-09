package com.ashy0019.hapticscape.desktop;

import com.ashy0019.hapticscape.protocol.LocalhostTransportEndpoint;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated process-local options used for side-by-side desktop clients. */
public final class DesktopLaunchOptions
{
	private static final Pattern PROFILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,31}");

	private final String profile;
	private final int gameplayPort;

	private DesktopLaunchOptions(String profile, int gameplayPort)
	{
		this.profile = profile;
		this.gameplayPort = gameplayPort;
	}

	public static DesktopLaunchOptions defaults()
	{
		return new DesktopLaunchOptions(null, LocalhostTransportEndpoint.DEFAULT_PORT);
	}

	public static DesktopLaunchOptions parse(String[] args)
	{
		Objects.requireNonNull(args, "args");
		String profile = null;
		int gameplayPort = LocalhostTransportEndpoint.DEFAULT_PORT;
		boolean profileSeen = false;
		boolean portSeen = false;
		for (int index = 0; index < args.length; index++)
		{
			String argument = Objects.requireNonNull(args[index], "argument");
			if (argument.startsWith("--profile="))
			{
				profileSeen = requireUnique(profileSeen, "--profile");
				profile = parseProfile(argument.substring("--profile=".length()));
			}
			else if ("--profile".equals(argument))
			{
				profileSeen = requireUnique(profileSeen, argument);
				profile = parseProfile(requireValue(args, ++index, argument));
			}
			else if (argument.startsWith("--gameplay-port="))
			{
				portSeen = requireUnique(portSeen, "--gameplay-port");
				gameplayPort = parsePort(argument.substring("--gameplay-port=".length()));
			}
			else if ("--gameplay-port".equals(argument))
			{
				portSeen = requireUnique(portSeen, argument);
				gameplayPort = parsePort(requireValue(args, ++index, argument));
			}
			else
			{
				throw new IllegalArgumentException("Unknown launch option: " + argument);
			}
		}
		return new DesktopLaunchOptions(profile, gameplayPort);
	}

	private static boolean requireUnique(boolean seen, String option)
	{
		if (seen)
		{
			throw new IllegalArgumentException(option + " may only be supplied once");
		}
		return true;
	}

	private static String requireValue(String[] args, int index, String option)
	{
		if (index >= args.length)
		{
			throw new IllegalArgumentException(option + " requires a value");
		}
		return args[index];
	}

	private static String parseProfile(String value)
	{
		String candidate = value == null ? "" : value.trim();
		if (!PROFILE.matcher(candidate).matches())
		{
			throw new IllegalArgumentException(
				"Profile must be 1-32 letters, numbers, dots, underscores, or hyphens"
			);
		}
		return candidate.toLowerCase(Locale.ROOT);
	}

	private static int parsePort(String value)
	{
		try
		{
			int port = Integer.parseInt(value);
			if (port < 1 || port > 65_535)
			{
				throw new NumberFormatException();
			}
			return port;
		}
		catch (NumberFormatException failure)
		{
			throw new IllegalArgumentException("Gameplay port must be between 1 and 65535");
		}
	}

	public boolean isNamedProfile()
	{
		return profile != null;
	}

	public String getProfile()
	{
		return profile;
	}

	public int getGameplayPort()
	{
		return gameplayPort;
	}

	public String getWindowTitle()
	{
		return isNamedProfile() ? "HapticScape — " + profile : "HapticScape";
	}
}
