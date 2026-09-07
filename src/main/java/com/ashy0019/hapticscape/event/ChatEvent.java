package com.ashy0019.hapticscape.event;

import java.util.Objects;

/**
 * Source-neutral chat observation. Source-specific message types and text
 * escaping are translated before this event reaches HapticScape logic.
 *
 * <p>The original source text is retained so the bridge does not throw away
 * information that a future consumer may need. Core policy should normally use
 * the normalized message.</p>
 */
public final class ChatEvent implements HapticScapeEvent
{
	public static final String TYPE = "chat";

	public enum Kind
	{
		DIRECT_MESSAGE,
		TRADE_REQUEST,
		OTHER
	}

	private final String source;
	private final Kind kind;
	private final String rawMessage;
	private final String normalizedMessage;

	public ChatEvent(
		String source,
		Kind kind,
		String rawMessage,
		String normalizedMessage)
	{
		this.source = requireIdentifier(source, "source");
		this.kind = Objects.requireNonNull(kind, "kind");
		this.rawMessage = rawMessage == null ? "" : rawMessage;
		this.normalizedMessage = normalizedMessage == null ? "" : normalizedMessage;
	}

	@Override
	public String getSource()
	{
		return source;
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	public Kind getKind()
	{
		return kind;
	}

	public String getRawMessage()
	{
		return rawMessage;
	}

	public String getNormalizedMessage()
	{
		return normalizedMessage;
	}

	/**
	 * Backward-compatible neutral accessor. Prefer {@link #getNormalizedMessage()}.
	 */
	public String getMessage()
	{
		return normalizedMessage;
	}

	private static String requireIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}
}
