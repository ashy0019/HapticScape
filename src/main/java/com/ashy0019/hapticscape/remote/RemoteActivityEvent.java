package com.ashy0019.hapticscape.remote;

import java.util.Objects;

/** Small, sanitized gameplay observation intended only for the controller feed. */
public final class RemoteActivityEvent
{
	private static final int MAXIMUM_LABEL_LENGTH = 64;
	private static final int MAXIMUM_DETAIL_LENGTH = 160;

	private final RemoteActivityType type;
	private final String label;
	private final String detail;
	private final long timestampMillis;

	public RemoteActivityEvent(
		RemoteActivityType type,
		String label,
		String detail,
		long timestampMillis)
	{
		this.type = Objects.requireNonNull(type, "type");
		this.label = sanitize(label, "label", MAXIMUM_LABEL_LENGTH);
		this.detail = sanitize(detail, "detail", MAXIMUM_DETAIL_LENGTH);
		this.timestampMillis = timestampMillis;
		validate();
	}

	public RemoteActivityType getType()
	{
		return type;
	}

	public String getLabel()
	{
		return label;
	}

	public String getDetail()
	{
		return detail;
	}

	public long getTimestampMillis()
	{
		return timestampMillis;
	}

	void validate()
	{
		Objects.requireNonNull(type, "type");
		if (label == null || label.isEmpty() || label.length() > MAXIMUM_LABEL_LENGTH
			|| hasLineBreak(label))
		{
			throw new IllegalArgumentException("Remote activity label is invalid");
		}
		if (detail == null || detail.length() > MAXIMUM_DETAIL_LENGTH || hasLineBreak(detail))
		{
			throw new IllegalArgumentException("Remote activity detail is invalid");
		}
		if (timestampMillis < 0)
		{
			throw new IllegalArgumentException("Remote activity timestamp is invalid");
		}
	}

	private static boolean hasLineBreak(String value)
	{
		return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
	}

	private static String sanitize(String value, String name, int maximumLength)
	{
		String normalized = Objects.requireNonNull(value, name)
			.replace('\r', ' ')
			.replace('\n', ' ')
			.trim();
		if (normalized.length() > maximumLength)
		{
			normalized = normalized.substring(0, maximumLength);
		}
		return normalized;
	}
}
