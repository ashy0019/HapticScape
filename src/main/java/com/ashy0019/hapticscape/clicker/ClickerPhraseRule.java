package com.ashy0019.hapticscape.clicker;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ClickerPhraseRule
{
	public static final int MAXIMUM_EXPRESSION_LENGTH = 500;

	private final String id;
	private final boolean enabled;
	private final ClickSequence sequence;
	private final ClickerPhraseMatchMode mode;
	private final String expression;
	private final String normalizedLiteral;
	private final Pattern regexPattern;

	public ClickerPhraseRule(
		boolean enabled,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		this(UUID.randomUUID().toString(), enabled, ClickSequence.ONE, mode, expression);
	}

	public ClickerPhraseRule(
		boolean enabled,
		ClickSequence sequence,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		this(UUID.randomUUID().toString(), enabled, sequence, mode, expression);
	}

	ClickerPhraseRule(
		String id,
		boolean enabled,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		this(id, enabled, ClickSequence.ONE, mode, expression);
	}

	ClickerPhraseRule(
		String id,
		boolean enabled,
		ClickSequence sequence,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		this.id = canonicalUuid(id);
		this.enabled = enabled;
		this.sequence = Objects.requireNonNull(sequence, "sequence");
		if (!sequence.isEnabled())
		{
			throw new IllegalArgumentException("Enabled phrase rules must request one to three clicks");
		}
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

	public String getId()
	{
		return id;
	}

	public ClickerPhraseRule withValues(
		boolean enabled,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		return new ClickerPhraseRule(id, enabled, sequence, mode, expression);
	}

	public ClickerPhraseRule withValues(
		boolean enabled,
		ClickSequence sequence,
		ClickerPhraseMatchMode mode,
		String expression)
	{
		return new ClickerPhraseRule(id, enabled, sequence, mode, expression);
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public ClickSequence getSequence()
	{
		return sequence;
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
		return id.equals(that.id)
			&& enabled == that.enabled
			&& sequence == that.sequence
			&& mode == that.mode
			&& expression.equals(that.expression);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, enabled, sequence, mode, expression);
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
			+ sequence.getClickCount()
			+ (sequence == ClickSequence.ONE ? " click · " : " clicks · ")
			+ mode
			+ ": "
			+ preview;
	}

	private static String canonicalUuid(String value)
	{
		try
		{
			String canonical = UUID.fromString(Objects.requireNonNull(value, "id")).toString();
			if (!canonical.equals(value.toLowerCase(Locale.ROOT)))
			{
				throw new IllegalArgumentException("Invalid phrase-rule ID");
			}
			return canonical;
		}
		catch (IllegalArgumentException | NullPointerException exception)
		{
			throw new IllegalArgumentException("Invalid phrase-rule ID", exception);
		}
	}
}
