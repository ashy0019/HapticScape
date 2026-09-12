using System;
using System.IO;
using System.Threading;

internal static class HapticScapeDeepLink
{
	internal static string Read(string[] args)
	{
		if (args.Length == 0
			|| !args[0].StartsWith("hapticscape:", StringComparison.OrdinalIgnoreCase))
		{
			return null;
		}
		if (args.Length != 1 || args[0].Length > 1024)
		{
			throw new InvalidOperationException("The HapticScape connection link is invalid.");
		}
		Uri uri;
		if (!Uri.TryCreate(args[0], UriKind.Absolute, out uri)
			|| !string.Equals(uri.Scheme, "hapticscape", StringComparison.OrdinalIgnoreCase)
			|| !string.Equals(uri.Host, "discord", StringComparison.OrdinalIgnoreCase)
			|| !string.IsNullOrEmpty(uri.UserInfo)
			|| !uri.IsDefaultPort
			|| !string.Equals(uri.AbsolutePath, "/accept", StringComparison.Ordinal)
			|| !string.IsNullOrEmpty(uri.Fragment)
			|| !IsValidQuery(uri.Query))
		{
			throw new InvalidOperationException("The HapticScape connection link is invalid.");
		}
		return uri.AbsoluteUri;
	}

	private static bool IsValidQuery(string query)
	{
		string value = query.StartsWith("?", StringComparison.Ordinal)
			? query.Substring(1)
			: query;
		string[] entries = value.Split('&');
		if (entries.Length != 3)
		{
			return false;
		}
		string controller = null;
		string request = null;
		string token = null;
		foreach (string entry in entries)
		{
			string[] pair = entry.Split(new[] { '=' }, 2);
			if (pair.Length != 2 || pair[1].Length == 0)
			{
				return false;
			}
			switch (pair[0])
			{
				case "controller":
					if (controller != null) return false;
					controller = pair[1];
					break;
				case "request":
					if (request != null) return false;
					request = pair[1];
					break;
				case "token":
					if (token != null) return false;
					token = pair[1];
					break;
				default:
					return false;
			}
		}
		return MatchesAscii(controller, 15, 22, true)
			&& MatchesAscii(request, 16, 16, false)
			&& MatchesAscii(token, 43, 43, false);
	}

	private static bool MatchesAscii(string value, int minimum, int maximum, bool digitsOnly)
	{
		if (value == null || value.Length < minimum || value.Length > maximum)
		{
			return false;
		}
		foreach (char character in value)
		{
			bool valid = character >= '0' && character <= '9';
			if (!digitsOnly)
			{
				valid = valid
					|| character >= 'A' && character <= 'Z'
					|| character >= 'a' && character <= 'z'
					|| character == '_'
					|| character == '-';
			}
			if (!valid)
			{
				return false;
			}
		}
		return true;
	}
}

internal static class DeepLinkInstanceHandoff
{
	internal static bool WaitForTakeover(
		Mutex instance,
		string requestPath,
		int timeoutMilliseconds)
	{
		if (instance == null)
		{
			throw new ArgumentNullException("instance");
		}
		if (requestPath == null)
		{
			throw new ArgumentNullException("requestPath");
		}
		if (timeoutMilliseconds < 0)
		{
			throw new ArgumentOutOfRangeException("timeoutMilliseconds");
		}

		DateTime deadline = DateTime.UtcNow.AddMilliseconds(timeoutMilliseconds);
		while (File.Exists(requestPath))
		{
			int remaining = (int)Math.Ceiling((deadline - DateTime.UtcNow).TotalMilliseconds);
			if (remaining <= 0)
			{
				return false;
			}
			try
			{
				if (instance.WaitOne(Math.Min(100, remaining)))
				{
					return true;
				}
			}
			catch (AbandonedMutexException)
			{
				return true;
			}
		}
		return false;
	}
}
