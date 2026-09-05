package com.ashy0019.hapticscape.clicker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class ClickerPhraseRules
{
	public static final int MAXIMUM_RULES = 50;

	private static final String LEGACY_FORMAT_VERSION = "v1";
	private static final String FORMAT_VERSION = "v2";
	private static final Base64.Encoder ENCODER =
		Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER =
		Base64.getUrlDecoder();

	private final List<ClickerPhraseRule> rules;

	private ClickerPhraseRules(List<ClickerPhraseRule> rules)
	{
		if (rules.size() > MAXIMUM_RULES)
		{
			throw new IllegalArgumentException(
				"Cannot store more than "
					+ MAXIMUM_RULES
					+ " phrase rules"
			);
		}
		HashSet<String> ids = new HashSet<>();
		for (ClickerPhraseRule rule : rules)
		{
			if (!ids.add(Objects.requireNonNull(rule, "rule").getId()))
			{
				throw new IllegalArgumentException("Duplicate phrase-rule ID");
			}
		}

		this.rules = Collections.unmodifiableList(
			new ArrayList<>(rules)
		);
	}

	public static ClickerPhraseRules empty()
	{
		return new ClickerPhraseRules(Collections.emptyList());
	}

	public static ClickerPhraseRules fromConfigValue(String configuredValue)
	{
		if (configuredValue == null || configuredValue.trim().isEmpty())
		{
			return empty();
		}

		String[] entries = configuredValue.split(";", -1);
		if (entries.length == 0
			|| (!FORMAT_VERSION.equals(entries[0])
				&& !LEGACY_FORMAT_VERSION.equals(entries[0])))
		{
			return empty();
		}
		boolean legacy = LEGACY_FORMAT_VERSION.equals(entries[0]);

		List<ClickerPhraseRule> restored = new ArrayList<>();

		for (int index = 1;
			index < entries.length && restored.size() < MAXIMUM_RULES;
			index++)
		{
			String entry = entries[index];
			if (entry.isEmpty())
			{
				continue;
			}

			String[] fields = entry.split(",", legacy ? 3 : 4);
			if (fields.length != (legacy ? 3 : 4))
			{
				continue;
			}

			boolean enabled;
			int enabledField = legacy ? 0 : 1;
			int modeField = legacy ? 1 : 2;
			int expressionField = legacy ? 2 : 3;
			if ("1".equals(fields[enabledField]))
			{
				enabled = true;
			}
			else if ("0".equals(fields[enabledField]))
			{
				enabled = false;
			}
			else
			{
				continue;
			}

			try
			{
				ClickerPhraseMatchMode mode =
					ClickerPhraseMatchMode.valueOf(
						fields[modeField].trim().toUpperCase(Locale.ROOT)
					);

				String expression = new String(
					DECODER.decode(fields[expressionField]),
					StandardCharsets.UTF_8
				);

				String id = legacy
					? legacyId(index, entry)
					: fields[0];
				ClickerPhraseRule rule = new ClickerPhraseRule(
					id,
					enabled,
					mode,
					expression
				);
				boolean duplicate = false;
				for (ClickerPhraseRule restoredRule : restored)
				{
					if (restoredRule.getId().equals(rule.getId()))
					{
						duplicate = true;
						break;
					}
				}
				if (!duplicate)
				{
					restored.add(rule);
				}
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore malformed, future, or invalid saved rules.
			}
		}

		return new ClickerPhraseRules(restored);
	}

	public static boolean requiresMigration(String configuredValue)
	{
		return configuredValue != null
			&& (configuredValue.equals(LEGACY_FORMAT_VERSION)
				|| configuredValue.startsWith(LEGACY_FORMAT_VERSION + ";"));
	}

	public List<ClickerPhraseRule> getRules()
	{
		return rules;
	}

	public boolean matches(String message)
	{
		for (ClickerPhraseRule rule : rules)
		{
			if (rule.matches(message))
			{
				return true;
			}
		}

		return false;
	}

	public ClickerPhraseRules withAdded(ClickerPhraseRule rule)
	{
		Objects.requireNonNull(rule, "rule");

		if (rules.size() >= MAXIMUM_RULES)
		{
			throw new IllegalStateException(
				"Maximum phrase rule count reached"
			);
		}

		List<ClickerPhraseRule> updated = new ArrayList<>(rules);
		updated.add(rule);
		return new ClickerPhraseRules(updated);
	}

	public ClickerPhraseRules withReplaced(
		int index,
		ClickerPhraseRule rule)
	{
		Objects.requireNonNull(rule, "rule");

		List<ClickerPhraseRule> updated = new ArrayList<>(rules);
		if (!updated.get(index).getId().equals(rule.getId()))
		{
			throw new IllegalArgumentException("Editing a phrase rule cannot change its ID");
		}
		updated.set(index, rule);
		return new ClickerPhraseRules(updated);
	}

	public ClickerPhraseRules withRemoved(int index)
	{
		List<ClickerPhraseRule> updated = new ArrayList<>(rules);
		updated.remove(index);
		return new ClickerPhraseRules(updated);
	}

	public String toConfigValue()
	{
		if (rules.isEmpty())
		{
			return "";
		}

		StringBuilder encoded = new StringBuilder(FORMAT_VERSION);
		for (ClickerPhraseRule rule : rules)
		{
			encoded
				.append(';')
				.append(rule.getId())
				.append(',')
				.append(rule.isEnabled() ? '1' : '0')
				.append(',')
				.append(rule.getMode().name())
				.append(',')
				.append(ENCODER.encodeToString(
					rule.getExpression().getBytes(StandardCharsets.UTF_8)
				));
		}

		return encoded.toString();
	}

	private static String legacyId(int index, String entry)
	{
		return UUID.nameUUIDFromBytes(
			("HapticScape phrase rule v1:" + index + ":" + entry)
				.getBytes(StandardCharsets.UTF_8)
		).toString();
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof ClickerPhraseRules))
		{
			return false;
		}

		ClickerPhraseRules that = (ClickerPhraseRules) other;
		return rules.equals(that.rules);
	}

	@Override
	public int hashCode()
	{
		return rules.hashCode();
	}
}
