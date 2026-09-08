package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.HapticScapeEvent;
import java.util.Collections;
import java.util.Set;
import java.util.Objects;

/**
 * Stateful receiver-side local event contract. A source must negotiate the
 * event protocol and advertise capabilities before event/state/reset messages
 * are accepted.
 */
public final class TransportSessionReceiver
{
	private final GameplayEventDispatcher dispatcher;
	private String source;
	private Set<SourceCapability> capabilities = Collections.emptySet();

	public TransportSessionReceiver(GameplayEventSink sink)
	{
		this.dispatcher = new GameplayEventDispatcher(Objects.requireNonNull(sink, "sink"));
	}

	public boolean isReady()
	{
		return source != null;
	}

	public String getSource()
	{
		return source;
	}

	public Set<SourceCapability> getCapabilities()
	{
		return capabilities;
	}

	/**
	 * Accepts one decoded transport message. Control messages may produce a
	 * response; event/state/reset messages return {@code null} on success.
	 */
	public TransportMessage receive(TransportMessage message)
	{
		Objects.requireNonNull(message, "message");
		if (!isReady())
		{
			return receiveBeforeHello(message);
		}

		if (message instanceof TransportMessage.Event)
		{
			HapticScapeEvent event = ((TransportMessage.Event) message).getEvent();
			TransportMessage.Error validation = validateEvent(event);
			if (validation != null)
			{
				return validation;
			}
			try
			{
				dispatcher.publish(event);
				return null;
			}
			catch (EventProtocolException ex)
			{
				return error("invalid_event", ex.getMessage());
			}
		}
		if (message instanceof TransportMessage.State)
		{
			HapticScapeEvent event = ((TransportMessage.State) message).getEvent();
			TransportMessage.Error validation = validateEvent(event);
			if (validation != null)
			{
				return validation;
			}
			try
			{
				dispatcher.seed(event);
				return null;
			}
			catch (EventProtocolException ex)
			{
				return error("invalid_state", ex.getMessage());
			}
		}
		if (message instanceof TransportMessage.Reset)
		{
			if (!source.equals(((TransportMessage.Reset) message).getSource()))
			{
				return error("source_mismatch", "Reset source does not match negotiated source");
			}
			dispatcher.resetSourceState();
			return null;
		}
		if (message instanceof TransportMessage.Hello)
		{
			return error("already_initialized", "Transport session has already completed hello");
		}
		return error("unexpected_message", "Message kind is not valid from the source peer");
	}

	private TransportMessage receiveBeforeHello(TransportMessage message)
	{
		if (!(message instanceof TransportMessage.Hello))
		{
			return error("hello_required", "First transport message must be hello");
		}

		TransportMessage.Hello hello = (TransportMessage.Hello) message;
		if (!EventProtocol.supports(hello.getEventProtocol()))
		{
			return error(
				"unsupported_event_protocol",
				"Unsupported event protocol: " + hello.getEventProtocol()
			);
		}
		if (hello.getEventVersion() != EventProtocol.VERSION)
		{
			return error(
				"unsupported_event_version",
				"Unsupported event protocol version: " + hello.getEventVersion()
			);
		}

		source = hello.getSource();
		capabilities = hello.getCapabilities();
		return new TransportMessage.HelloAck(
			hello.getEventProtocol(),
			EventProtocol.VERSION,
			capabilities
		);
	}

	private TransportMessage.Error validateEvent(HapticScapeEvent event)
	{
		if (!source.equals(event.getSource()))
		{
			return error("source_mismatch", "Event source does not match negotiated source");
		}
		final SourceCapability required;
		try
		{
			required = SourceCapability.forEvent(event);
		}
		catch (EventProtocolException ex)
		{
			return error("unsupported_event", ex.getMessage());
		}
		if (!capabilities.contains(required))
		{
			return error(
				"capability_not_declared",
				"Event requires undeclared source capability: " + required.getWireName()
			);
		}
		return null;
	}

	private static TransportMessage.Error error(String code, String message)
	{
		return new TransportMessage.Error(code, message == null ? "" : message);
	}
}
