package com.ashy0019.hapticscape.clicker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ClickerPhraseRules
{
	public static final int MAXIMUM_RULES = 50;

	private static final String FORMAT_VERSION = "v1";
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
		if (entries.length == 0 || !FORMAT_VERSION.equals(entries[0]))
		{
			return empty();
		}

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

			String[] fields = entry.split(",", 3);
			if (fields.length != 3)
			{
				continue;
			}

			boolean enabled;
			if ("1".equals(fields[0]))
			{
				enabled = true;
			}
			else if ("0".equals(fields[0]))
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
						fields[1].trim().toUpperCase(Locale.ROOT)
					);

				String expression = new String(
					DECODER.decode(fields[2]),
					StandardCharsets.UTF_8
				);

				restored.add(new ClickerPhraseRule(
					enabled,
					mode,
					expression
				));
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore malformed, future, or invalid saved rules.
			}
		}

		return new ClickerPhraseRules(restored);
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