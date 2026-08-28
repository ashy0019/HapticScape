package com.ashy0019.hapticscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Immutable collection of custom patterns.
 *
 * <p>The v3 format stores a monotonically increasing next id as well as the
 * current entries. IDs are therefore not recycled after deletion, which keeps
 * saved pattern assignments unambiguous.</p>
 */
public final class CustomPatternLibrary
{
	public static final int MAXIMUM_PATTERN_COUNT = 100;

	private static final String VERSION_THREE_PREFIX = "v3|";
	private static final String VERSION_TWO_PREFIX = "v2|";
	private static final int MAXIMUM_NAME_LENGTH = 18;

	private final List<CustomPatternEntry> patterns;
	private final int nextPatternId;

	private CustomPatternLibrary(List<CustomPatternEntry> patterns, int nextPatternId)
	{
		if (patterns.isEmpty() || patterns.size() > MAXIMUM_PATTERN_COUNT)
		{
			throw new IllegalArgumentException("A library must contain between 1 and 100 patterns");
		}

		List<CustomPatternEntry> copy = new ArrayList<>(patterns.size());
		Set<Integer> ids = new HashSet<>();
		int greatestId = 0;
		for (CustomPatternEntry entry : patterns)
		{
			if (entry.getId() <= 0 || !ids.add(entry.getId()))
			{
				throw new IllegalArgumentException("Pattern ids must be positive and unique");
			}
			copy.add(new CustomPatternEntry(
				entry.getId(),
				sanitizeName(entry.getName(), entry.getId()),
				entry.getPattern()
			));
			greatestId = Math.max(greatestId, entry.getId());
		}
		if (nextPatternId <= greatestId)
		{
			throw new IllegalArgumentException("The next pattern id must be unused");
		}

		this.patterns = Collections.unmodifiableList(copy);
		this.nextPatternId = nextPatternId;
	}

	public static CustomPatternLibrary defaults()
	{
		return new CustomPatternLibrary(
			Collections.singletonList(blankEntry(1)),
			2
		);
	}

	public static CustomPatternLibrary fromConfigValue(String configuredValue)
	{
		if (configuredValue == null)
		{
			return defaults();
		}

		String value = configuredValue.trim();
		if (value.startsWith(VERSION_THREE_PREFIX))
		{
			return parseVersionThree(value);
		}
		if (value.startsWith(VERSION_TWO_PREFIX))
		{
			return migrateVersionTwo(value);
		}
		return defaults();
	}

	public List<CustomPatternEntry> getPatterns()
	{
		return patterns;
	}

	public Optional<CustomPatternEntry> findById(int id)
	{
		for (CustomPatternEntry entry : patterns)
		{
			if (entry.getId() == id)
			{
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}

	public boolean contains(int id)
	{
		return findById(id).isPresent();
	}

	public int size()
	{
		return patterns.size();
	}

	public boolean canAddPattern()
	{
		return patterns.size() < MAXIMUM_PATTERN_COUNT;
	}

	public int getNextPatternId()
	{
		return nextPatternId;
	}

	public CustomPatternLibrary addBlankPattern()
	{
		if (!canAddPattern())
		{
			return this;
		}

		List<CustomPatternEntry> updated = new ArrayList<>(patterns);
		updated.add(new CustomPatternEntry(
			nextPatternId,
			defaultName(nextDefaultNameNumber()),
			CustomPattern.silent()
		));
		return new CustomPatternLibrary(updated, nextPatternId + 1);
	}

	public CustomPatternLibrary withPattern(int id, CustomPattern pattern)
	{
		return replace(id, entry -> entry.withPattern(pattern));
	}

	public CustomPatternLibrary withName(int id, String name)
	{
		return replace(id, entry -> entry.withName(sanitizeName(name, id)));
	}

	public CustomPatternLibrary withoutPattern(int id)
	{
		if (patterns.size() == 1 || !contains(id))
		{
			return this;
		}

		List<CustomPatternEntry> updated = new ArrayList<>(patterns);
		updated.removeIf(entry -> entry.getId() == id);
		return new CustomPatternLibrary(updated, nextPatternId);
	}

	public String toConfigValue()
	{
		StringJoiner entries = new StringJoiner(";");
		for (CustomPatternEntry entry : patterns)
		{
			entries.add(
				entry.getId()
					+ "=" + entry.getName()
					+ "," + entry.getPattern().toConfigValue()
			);
		}
		return VERSION_THREE_PREFIX + nextPatternId + "|" + entries;
	}

	private CustomPatternLibrary replace(int id, EntryUpdate update)
	{
		List<CustomPatternEntry> updated = new ArrayList<>(patterns);
		for (int index = 0; index < updated.size(); index++)
		{
			CustomPatternEntry entry = updated.get(index);
			if (entry.getId() == id)
			{
				updated.set(index, update.apply(entry));
				return new CustomPatternLibrary(updated, nextPatternId);
			}
		}
		return this;
	}

	private static CustomPatternLibrary parseVersionThree(String value)
	{
		String body = value.substring(VERSION_THREE_PREFIX.length());
		String[] sections = body.split("\\|", 2);
		if (sections.length != 2)
		{
			return defaults();
		}

		try
		{
			int configuredNextId = Integer.parseInt(sections[0]);
			List<CustomPatternEntry> parsed = parseEntries(sections[1], false);
			if (parsed.isEmpty())
			{
				return defaults();
			}
			int greatestId = parsed.stream()
				.mapToInt(CustomPatternEntry::getId)
				.max()
				.orElse(0);
			return new CustomPatternLibrary(parsed, Math.max(configuredNextId, greatestId + 1));
		}
		catch (IllegalArgumentException e)
		{
			return defaults();
		}
	}

	private static CustomPatternLibrary migrateVersionTwo(String value)
	{
		String entries = value.substring(VERSION_TWO_PREFIX.length());
		List<CustomPatternEntry> parsed = parseEntries(entries, true);
		if (parsed.isEmpty())
		{
			return defaults();
		}
		int greatestId = parsed.stream()
			.mapToInt(CustomPatternEntry::getId)
			.max()
			.orElse(0);
		return new CustomPatternLibrary(parsed, greatestId + 1);
	}

	private static List<CustomPatternEntry> parseEntries(String entries, boolean legacySlots)
	{
		List<CustomPatternEntry> parsed = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		for (String encodedEntry : entries.split(";"))
		{
			if (parsed.size() == MAXIMUM_PATTERN_COUNT)
			{
				break;
			}

			String[] entryFields = encodedEntry.split("=", 2);
			if (entryFields.length != 2)
			{
				continue;
			}
			String[] valueFields = entryFields[1].split(",", 2);
			if (valueFields.length != 2)
			{
				continue;
			}

			try
			{
				int id = legacySlots
					? legacySlotId(entryFields[0])
					: Integer.parseInt(entryFields[0]);
				if (id <= 0 || !ids.add(id))
				{
					continue;
				}
				String name = migrateLegacyName(valueFields[0], id, legacySlots);
				parsed.add(new CustomPatternEntry(
					id,
					sanitizeName(name, id),
					CustomPattern.fromConfigValue(valueFields[1])
				));
			}
			catch (IllegalArgumentException ignored)
			{
				// Skip malformed entries while preserving everything else.
			}
		}
		return parsed;
	}

	private static int legacySlotId(String slot)
	{
		switch (slot)
		{
			case "I":
				return 1;
			case "II":
				return 2;
			case "III":
				return 3;
			case "IV":
				return 4;
			default:
				throw new IllegalArgumentException("Unknown legacy slot");
		}
	}

	private static String migrateLegacyName(String name, int id, boolean legacy)
	{
		if (legacy && ("Rune " + romanNumeral(id)).equals(name))
		{
			return defaultName(id);
		}
		return name;
	}

	private static String romanNumeral(int id)
	{
		switch (id)
		{
			case 1:
				return "I";
			case 2:
				return "II";
			case 3:
				return "III";
			case 4:
				return "IV";
			default:
				return "";
		}
	}

	private static CustomPatternEntry blankEntry(int id)
	{
		return new CustomPatternEntry(id, defaultName(id), CustomPattern.silent());
	}

	private int nextDefaultNameNumber()
	{
		int highestNumber = 0;
		for (CustomPatternEntry entry : patterns)
		{
			String name = entry.getName();
			if (!name.startsWith("Custom "))
			{
				continue;
			}

			try
			{
				int number = Integer.parseInt(name.substring("Custom ".length()));
				if (number > highestNumber && number < Integer.MAX_VALUE)
				{
					highestNumber = number;
				}
			}
			catch (NumberFormatException ignored)
			{
				// A manually chosen name beginning with "Custom" is not a slot number.
			}
		}
		return highestNumber + 1;
	}

	private static String defaultName(int id)
	{
		return "Custom " + id;
	}

	private static String sanitizeName(String name, int id)
	{
		String safe = name == null ? "" : name
			.replace(',', ' ')
			.replace(';', ' ')
			.replace('=', ' ')
			.replace('|', ' ')
			.trim()
			.replaceAll("\\s+", " ");
		if (safe.isEmpty())
		{
			return defaultName(id);
		}
		return safe.length() <= MAXIMUM_NAME_LENGTH
			? safe
			: safe.substring(0, MAXIMUM_NAME_LENGTH).trim();
	}

	private interface EntryUpdate
	{
		CustomPatternEntry apply(CustomPatternEntry entry);
	}
}
