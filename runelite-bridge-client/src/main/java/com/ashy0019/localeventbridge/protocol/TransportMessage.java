package com.ashy0019.localeventbridge.protocol;

import com.ashy0019.localeventbridge.event.LocalEvent;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Typed messages carried by the source-to-local-consumer event transport. */
public interface TransportMessage
{
	enum Kind
	{
		HELLO,
		HELLO_ACK,
		EVENT,
		STATE,
		RESET,
		ERROR
	}

	Kind getKind();

	final class Hello implements TransportMessage
	{
		private final String source;
		private final String eventProtocol;
		private final int eventVersion;
		private final Set<SourceCapability> capabilities;

		public Hello(
			String source,
			String eventProtocol,
			int eventVersion,
			Set<SourceCapability> capabilities)
		{
			this.source = requireIdentifier(source, "source");
			this.eventProtocol = requireIdentifier(eventProtocol, "eventProtocol");
			if (eventVersion <= 0)
			{
				throw new IllegalArgumentException("eventVersion must be positive");
			}
			this.eventVersion = eventVersion;
			this.capabilities = immutableCapabilities(capabilities);
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

		public Set<SourceCapability> getCapabilities()
		{
			return capabilities;
		}
	}

	final class HelloAck implements TransportMessage
	{
		private final String eventProtocol;
		private final int eventVersion;
		private final Set<SourceCapability> capabilities;

		public HelloAck(
			String eventProtocol,
			int eventVersion,
			Set<SourceCapability> capabilities)
		{
			this.eventProtocol = requireIdentifier(eventProtocol, "eventProtocol");
			if (eventVersion <= 0)
			{
				throw new IllegalArgumentException("eventVersion must be positive");
			}
			this.eventVersion = eventVersion;
			this.capabilities = immutableCapabilities(capabilities);
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

		public Set<SourceCapability> getCapabilities()
		{
			return capabilities;
		}
	}

	final class Event implements TransportMessage
	{
		private final LocalEvent event;

		public Event(LocalEvent event)
		{
			this.event = Objects.requireNonNull(event, "event");
		}

		@Override
		public Kind getKind()
		{
			return Kind.EVENT;
		}

		public LocalEvent getEvent()
		{
			return event;
		}
	}

	/** Snapshot/state seed delivered without triggering edge-based feedback. */
	final class State implements TransportMessage
	{
		private final LocalEvent event;

		public State(LocalEvent event)
		{
			this.event = Objects.requireNonNull(event, "event");
		}

		@Override
		public Kind getKind()
		{
			return Kind.STATE;
		}

		public LocalEvent getEvent()
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

	static Set<SourceCapability> immutableCapabilities(Set<SourceCapability> capabilities)
	{
		Objects.requireNonNull(capabilities, "capabilities");
		if (capabilities.isEmpty())
		{
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
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
