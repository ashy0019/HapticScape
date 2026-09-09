using System;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

internal sealed class HapticScapeLaunchOptions
{
	private static readonly Regex ProfilePattern = new Regex(
		"^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$",
		RegexOptions.CultureInvariant);

	internal string Profile { get; private set; }
	internal int GameplayPort { get; private set; }
	internal bool IsNamedProfile { get { return Profile != null; } }
	internal bool IsDefault { get { return !IsNamedProfile && GameplayPort == 41713; } }
	internal string MutexName
	{
		get
		{
			return @"Local\HapticScape.Client"
				+ (IsNamedProfile ? "." + Profile : "");
		}
	}

	private HapticScapeLaunchOptions(string profile, int gameplayPort)
	{
		Profile = profile;
		GameplayPort = gameplayPort;
	}

	internal static HapticScapeLaunchOptions Defaults()
	{
		return new HapticScapeLaunchOptions(null, 41713);
	}

	internal static HapticScapeLaunchOptions Parse(string[] args)
	{
		string profile = null;
		int gameplayPort = 41713;
		bool profileSeen = false;
		bool portSeen = false;
		for (int index = 0; index < args.Length; index++)
		{
			string argument = args[index];
			if (argument.StartsWith("--profile=", StringComparison.Ordinal))
			{
				RequireUnique(ref profileSeen, "--profile");
				profile = ParseProfile(argument.Substring("--profile=".Length));
			}
			else if (string.Equals(argument, "--profile", StringComparison.Ordinal))
			{
				RequireUnique(ref profileSeen, argument);
				profile = ParseProfile(RequireValue(args, ref index, argument));
			}
			else if (argument.StartsWith("--gameplay-port=", StringComparison.Ordinal))
			{
				RequireUnique(ref portSeen, "--gameplay-port");
				gameplayPort = ParsePort(argument.Substring("--gameplay-port=".Length));
			}
			else if (string.Equals(argument, "--gameplay-port", StringComparison.Ordinal))
			{
				RequireUnique(ref portSeen, argument);
				gameplayPort = ParsePort(RequireValue(args, ref index, argument));
			}
			else
			{
				throw new InvalidOperationException("Unknown launch option: " + argument);
			}
		}
		return new HapticScapeLaunchOptions(profile, gameplayPort);
	}

	internal string JavaArguments()
	{
		StringBuilder arguments = new StringBuilder();
		if (IsNamedProfile)
		{
			arguments.Append(" --profile ").Append(Profile);
		}
		if (GameplayPort != 41713)
		{
			arguments.Append(" --gameplay-port ")
				.Append(GameplayPort.ToString(CultureInfo.InvariantCulture));
		}
		return arguments.ToString();
	}

	private static string RequireValue(string[] args, ref int index, string option)
	{
		index++;
		if (index >= args.Length)
		{
			throw new InvalidOperationException(option + " requires a value");
		}
		return args[index];
	}

	private static void RequireUnique(ref bool seen, string option)
	{
		if (seen)
		{
			throw new InvalidOperationException(option + " may only be supplied once");
		}
		seen = true;
	}

	private static string ParseProfile(string value)
	{
		string candidate = value == null ? "" : value.Trim();
		if (!ProfilePattern.IsMatch(candidate))
		{
			throw new InvalidOperationException(
				"Profile must be 1-32 letters, numbers, dots, underscores, or hyphens");
		}
		return candidate.ToLowerInvariant();
	}

	private static int ParsePort(string value)
	{
		int port;
		if (!int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out port)
			|| port < 1 || port > 65535)
		{
			throw new InvalidOperationException("Gameplay port must be between 1 and 65535");
		}
		return port;
	}
}
