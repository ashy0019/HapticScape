package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.HapticScapeEvent;
import java.util.Objects;

/**
 * Stateful receiver-side transport contract. A source must negotiate an event
 * protocol before gameplay or reset messages are accepted.
 */
public final class TransportSessionReceiver
{
	private final GameplayEventDispatcher dispatcher;
	private String source;

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

	/**
	 * Accepts one decoded transport message. Control messages may produce a
	 * response; gameplay/seed/reset messages return {@code null} on success.
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
			TransportMessage.Event eventMessage = (TransportMessage.Event) message;
			HapticScapeEvent event = eventMessage.getEvent();
			if (!source.equals(event.getSource()))
			{
				return error("source_mismatch", "Event source does not match negotiated source");
			}
			try
			{
				if (eventMessage.getOperation() == TransportMessage.EventOperation.PUBLISH)
				{
					dispatcher.publish(event);
				}
				else
				{
					dispatcher.seed(event);
				}
				return null;
			}
			catch (EventProtocolException ex)
			{
				return error("invalid_event_operation", ex.getMessage());
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
		if (!EventProtocol.NAME.equals(hello.getEventProtocol()))
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
		return new TransportMessage.HelloAck(EventProtocol.NAME, EventProtocol.VERSION);
	}

	private static TransportMessage.Error error(String code, String message)
	{
		return new TransportMessage.Error(code, message == null ? "" : message);
	}
}
