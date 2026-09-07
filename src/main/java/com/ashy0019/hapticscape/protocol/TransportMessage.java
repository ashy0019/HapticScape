package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.event.HapticScapeEvent;
import java.util.Objects;

/** Typed messages carried by the source-to-HapticScape transport. */
public interface TransportMessage
{
	enum Kind
	{
		HELLO,
		HELLO_ACK,
		EVENT,
		RESET,
		ERROR
	}

	enum EventOperation
	{
		PUBLISH,
		SEED
	}

	Kind getKind();

	final class Hello implements TransportMessage
	{
		private final String source;
		private final String eventProtocol;
		private final int eventVersion;

		public Hello(String source, String eventProtocol, int eventVersion)
		{
			this.source = requireIdentifier(source, "source");
			this.eventProtocol = requireIdentifier(eventProtocol, "eventProtocol");
			if (eventVersion <= 0)
			{
				throw new IllegalArgumentException("eventVersion must be positive");
			}
			this.eventVersion = eventVersion;
		}

		@Override
		public Kind getKind()
		{
			return Kind.HELLO;
		}

		public String getSource()
		{
			return source;
		}

		public String getEventProtocol()
		{
			return eventProtocol;
		}

		public int getEventVersion()
		{
			return eventVersion;
		}
	}

	final class HelloAck implements TransportMessage
	{
		private final String eventProtocol;
		private final int eventVersion;

		public HelloAck(String eventProtocol, int eventVersion)
		{
			this.eventProtocol = requireIdentifier(eventProtocol, "eventProtocol");
			if (eventVersion <= 0)
			{
				throw new IllegalArgumentException("eventVersion must be positive");
			}
			this.eventVersion = eventVersion;
		}

		@Override
		public Kind getKind()
		{
			return Kind.HELLO_ACK;
		}

		public String getEventProtocol()
		{
			return eventProtocol;
		}

		public int getEventVersion()
		{
			return eventVersion;
		}
	}

	final class Event implements TransportMessage
	{
		private final EventOperation operation;
		private final HapticScapeEvent event;

		public Event(EventOperation operation, HapticScapeEvent event)
		{
			this.operation = Objects.requireNonNull(operation, "operation");
			this.event = Objects.requireNonNull(event, "event");
		}

		@Override
		public Kind getKind()
		{
			return Kind.EVENT;
		}

		public EventOperation getOperation()
		{
			return operation;
		}

		public HapticScapeEvent getEvent()
		{
			return event;
		}
	}

	final class Reset implements TransportMessage
	{
		private final String source;

		public Reset(String source)
		{
			this.source = requireIdentifier(source, "source");
		}

		@Override
		public Kind getKind()
		{
			return Kind.RESET;
		}

		public String getSource()
		{
			return source;
		}
	}

	final class Error implements TransportMessage
	{
		private final String code;
		private final String message;

		public Error(String code, String message)
		{
			this.code = requireIdentifier(code, "code");
			this.message = Objects.requireNonNull(message, "message");
		}

		@Override
		public Kind getKind()
		{
			return Kind.ERROR;
		}

		public String getCode()
		{
			return code;
		}

		public String getMessage()
		{
			return message;
		}
	}

	static String requireIdentifier(String value, String name)
	{
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return normalized;
	}
}
