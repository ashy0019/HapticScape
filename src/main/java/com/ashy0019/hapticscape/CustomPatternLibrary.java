package com.ashy0019.hapticscape;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class CustomPatternLibrary
{
	private static final String VERSION_PREFIX = "v2|";
	private static final int MAXIMUM_NAME_LENGTH = 18;

	private final Map<CustomPatternSlot, CustomPattern> patterns;
	private final Map<CustomPatternSlot, String> names;

	private CustomPatternLibrary(
		Map<CustomPatternSlot, CustomPattern> patterns,
		Map<CustomPatternSlot, String> names)
	{
		EnumMap<CustomPatternSlot, CustomPattern> patternCopy = defaultPatterns();
		patternCopy.putAll(patterns);
		this.patterns = Collections.unmodifiableMap(patternCopy);

		EnumMap<CustomPatternSlot, String> nameCopy = defaultNames();
		for (Map.Entry<CustomPatternSlot, String> entry : names.entrySet())
		{
			nameCopy.put(entry.getKey(), sanitizeName(entry.getValue(), entry.getKey()));
		}
		this.names = Collections.unmodifiableMap(nameCopy);
	}

	public static CustomPatternLibrary defaults()
	{
		return new CustomPatternLibrary(Collections.emptyMap(), Collections.emptyMap());
	}

	public static CustomPatternLibrary fromConfigValue(String configuredValue)
	{
		if (configuredValue == null || !configuredValue.trim().startsWith(VERSION_PREFIX))
		{
			return defaults();
		}

		EnumMap<CustomPatternSlot, CustomPattern> patterns =
			new EnumMap<>(CustomPatternSlot.class);
		EnumMap<CustomPatternSlot, String> names = new EnumMap<>(CustomPatternSlot.class);
		String entries = configuredValue.trim().substring(VERSION_PREFIX.length());
		for (String entry : entries.split(";"))
		{
			String[] slotFields = entry.split("=", 2);
			if (slotFields.length != 2)
			{
				continue;
			}

			String[] valueFields = slotFields[1].split(",", 2);
			if (valueFields.length != 2)
			{
				continue;
			}

			try
			{
				CustomPatternSlot slot = CustomPatternSlot.valueOf(slotFields[0]);
				patterns.put(slot, CustomPattern.fromConfigValue(valueFields[1]));
				names.put(slot, sanitizeName(valueFields[0], slot));
			}
			catch (IllegalArgumentException ignored)
			{
				// Preserve the default for malformed or unknown entries.
			}
		}
		return new CustomPatternLibrary(patterns, names);
	}

	public CustomPattern get(CustomPatternSlot slot)
	{
		return patterns.get(Objects.requireNonNull(slot, "slot"));
	}

	public String getName(CustomPatternSlot slot)
	{
		return names.get(Objects.requireNonNull(slot, "slot"));
	}

	public CustomPatternLibrary withPattern(CustomPatternSlot slot, CustomPattern pattern)
	{
		EnumMap<CustomPatternSlot, CustomPattern> updated = new EnumMap<>(CustomPatternSlot.class);
		updated.putAll(patterns);
		updated.put(
			Objects.requireNonNull(slot, "slot"),
			Objects.requireNonNull(pattern, "pattern")
		);
		return new CustomPatternLibrary(updated, names);
	}

	public CustomPatternLibrary withName(CustomPatternSlot slot, String name)
	{
		EnumMap<CustomPatternSlot, String> updated = new EnumMap<>(CustomPatternSlot.class);
		updated.putAll(names);
		updated.put(
			Objects.requireNonNull(slot, "slot"),
			sanitizeName(name, slot)
		);
		return new CustomPatternLibrary(patterns, updated);
	}

	public String toConfigValue()
	{
		StringJoiner entries = new StringJoiner(";");
		for (CustomPatternSlot slot : CustomPatternSlot.values())
		{
			entries.add(
				slot.name()
					+ "=" + getName(slot)
					+ "," + get(slot).toConfigValue()
			);
		}
		return VERSION_PREFIX + entries;
	}

	private static EnumMap<CustomPatternSlot, CustomPattern> defaultPatterns()
	{
		EnumMap<CustomPatternSlot, CustomPattern> defaults =
			new EnumMap<>(CustomPatternSlot.class);
		for (CustomPatternSlot slot : CustomPatternSlot.values())
		{
			defaults.put(slot, CustomPattern.silent());
		}
		return defaults;
	}

	private static EnumMap<CustomPatternSlot, String> defaultNames()
	{
		EnumMap<CustomPatternSlot, String> defaults = new EnumMap<>(CustomPatternSlot.class);
		for (CustomPatternSlot slot : CustomPatternSlot.values())
		{
			defaults.put(slot, slot.getDefaultName());
		}
		return defaults;
	}

	private static String sanitizeName(String name, CustomPatternSlot slot)
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
			return slot.getDefaultName();
		}
		if (safe.equals(legacyDefaultName(slot)))
		{
			return slot.getDefaultName();
		}
		return safe.length() <= MAXIMUM_NAME_LENGTH
			? safe
			: safe.substring(0, MAXIMUM_NAME_LENGTH).trim();
	}

	private static String legacyDefaultName(CustomPatternSlot slot)
	{
		switch (slot)
		{
			case I:
				return "Rune I";
			case II:
				return "Rune II";
			case III:
				return "Rune III";
			case IV:
			default:
				return "Rune IV";
		}
	}
}
