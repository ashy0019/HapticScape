package com.ashy0019.hapticscape.clicker;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ClickerPhraseRule
{
	public static final int MAXIMUM_EXPRESSION_LENGTH = 500;

	private final boolean enabled;
	private final ClickerPhraseMatchMode mode;
	private final String expression;
	private final String normalizedLiteral;
	private final Pattern regexPattern;

	public ClickerPhraseRule(
		boolean enabled,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		this.enabled = enabled;
		this.mode = Objects.requireNonNull(mode, "mode");
		this.expression = Objects.requireNonNull(expression, "expression");

		if (expression.trim().isEmpty())
		{
			throw new IllegalArgumentException("Phrase cannot be blank");
		}

		if (expression.length() > MAXIMUM_EXPRESSION_LENGTH)
		{
			throw new IllegalArgumentException(
				"Phrase cannot exceed "
					+ MAXIMUM_EXPRESSION_LENGTH
					+ " characters"
			);
		}

		normalizedLiteral = mode == ClickerPhraseMatchMode.REGEX
			? null
			: expression.toLowerCase(Locale.ROOT);

		regexPattern = mode == ClickerPhraseMatchMode.REGEX
			? Pattern.compile(expression)
			: null;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public ClickerPhraseMatchMode getMode()
	{
		return mode;
	}

	public String getExpression()
	{
		return expression;
	}

	public boolean matches(String message)
	{
		if (!enabled || message == null)
		{
			return false;
		}

		switch (mode)
		{
			case CONTAINS:
				return message.toLowerCase(Locale.ROOT).contains(normalizedLiteral);
			case EXACT:
				return message.toLowerCase(Locale.ROOT).equals(normalizedLiteral);
			case REGEX:
				return regexPattern.matcher(message).find();
			default:
				return false;
		}
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof ClickerPhraseRule))
		{
			return false;
		}

		ClickerPhraseRule that = (ClickerPhraseRule) other;
		return enabled == that.enabled
			&& mode == that.mode
			&& expression.equals(that.expression);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(enabled, mode, expression);
	}

	@Override
	public String toString()
	{
		String preview = expression
			.replace('\n', ' ')
			.replace('\r', ' ');

		if (preview.length() > 60)
		{
			preview = preview.substring(0, 57) + "...";
		}

		return (enabled ? "" : "(off) ")
			+ mode
			+ ": "
			+ preview;
	}
}