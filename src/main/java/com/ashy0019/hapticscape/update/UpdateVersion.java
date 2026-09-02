package com.ashy0019.hapticscape.update;

import java.util.Optional;

final class UpdateVersion implements Comparable<UpdateVersion>
{
	private final int[] parts;

	private UpdateVersion(int[] parts)
	{
		this.parts = parts;
	}

	static Optional<UpdateVersion> parse(String value)
	{
		if (value == null)
		{
			return Optional.empty();
		}
		String normalized = value.trim();
		if (normalized.startsWith("v") || normalized.startsWith("V"))
		{
			normalized = normalized.substring(1);
		}
		if (normalized.isEmpty() || normalized.contains("-") || normalized.contains("+"))
		{
			return Optional.empty();
		}
		String[] textParts = normalized.split("\\.", -1);
		if (textParts.length < 2 || textParts.length > 4)
		{
			return Optional.empty();
		}
		int[] parts = new int[4];
		try
		{
			for (int index = 0; index < textParts.length; index++)
			{
				if (textParts[index].isEmpty())
				{
					return Optional.empty();
				}
				parts[index] = Integer.parseInt(textParts[index]);
				if (parts[index] < 0)
				{
					return Optional.empty();
				}
			}
		}
		catch (NumberFormatException exception)
		{
			return Optional.empty();
		}
		return Optional.of(new UpdateVersion(parts));
	}

	static boolean isNewer(String candidate, String installed)
	{
		Optional<UpdateVersion> candidateVersion = parse(candidate);
		Optional<UpdateVersion> installedVersion = parse(installed);
		return candidateVersion.isPresent()
			&& installedVersion.isPresent()
			&& candidateVersion.get().compareTo(installedVersion.get()) > 0;
	}

	@Override
	public int compareTo(UpdateVersion other)
	{
		for (int index = 0; index < parts.length; index++)
		{
			int comparison = Integer.compare(parts[index], other.parts[index]);
			if (comparison != 0)
			{
				return comparison;
			}
		}
		return 0;
	}
}
