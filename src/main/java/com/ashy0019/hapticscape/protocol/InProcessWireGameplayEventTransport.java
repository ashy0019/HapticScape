package com.ashy0019.hapticscape.protocol;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.HapticScapeEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Objects;
import java.util.Set;

/**
 * In-process proof transport that exercises the full transport framing,
 * handshake, event-wire codec, capability negotiation, and receiver dispatch contract without a socket.
 */
public final class InProcessWireGameplayEventTransport implements GameplayEventSink
{
	private final String source;
	private final TransportWireCodec codec;
	private final Set<SourceCapability> capabilities;
	private final TransportSessionReceiver receiver;

	public InProcessWireGameplayEventTransport(
		String source,
		TransportWireCodec codec,
		Set<SourceCapability> capabilities,
		GameplayEventSink downstream)
	{
		this.source = TransportMessage.requireIdentifier(source, "source");
		this.codec = Objects.requireNonNull(codec, "codec");
		this.capabilities = TransportMessage.immutableCapabilities(capabilities);
		this.receiver = new TransportSessionReceiver(
			Objects.requireNonNull(downstream, "downstream")
		);
		negotiate();
	}

	@Override
	public void resetSourceState()
	{
		send(new TransportMessage.Reset(source));
	}

	@Override
	public void seedVitals(VitalsChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void seedInventory(InventoryChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void seedToxicStatus(ToxicStatusChangedEvent event)
	{
		seed(event);
	}

	@Override
	public void onXpEvent(XpEvent event)
	{
		publish(event);
	}

	@Override
	public void onChatEvent(ChatEvent event)
	{
		publish(event);
	}

	@Override
	public void onVitalsEvent(VitalsChangedEvent event)
	{
		publish(event);
	}

	@Override
	public void onInventoryEvent(InventoryChangedEvent event)
	{
		publish(event);
	}

	@Override
	public void onToxicStatusEvent(ToxicStatusChangedEvent event)
	{
		publish(event);
	}

	@Override
	public void onLootEvent(LootReceivedEvent event)
	{
		publish(event);
	}

	@Override
	public void onPlayerDeathEvent(PlayerDeathEvent event)
	{
		publish(event);
	}

	@Override
	public void onNotificationEvent(NotificationEvent event)
	{
		publish(event);
	}

	private void negotiate()
	{
		TransportMessage response = roundTripResponse(new TransportMessage.Hello(
			source,
			EventProtocol.NAME,
			EventProtocol.VERSION,
			capabilities
		));
		if (!(response instanceof TransportMessage.HelloAck))
		{
			throw rejection(response, "Transport hello was not acknowledged");
		}
		TransportMessage.HelloAck ack = (TransportMessage.HelloAck) response;
		if (!EventProtocol.NAME.equals(ack.getEventProtocol())
			|| ack.getEventVersion() != EventProtocol.VERSION
			|| !capabilities.equals(ack.getCapabilities()))
		{
			throw new TransportProtocolException("Transport hello acknowledged an incompatible source contract");
		}
	}

	private void publish(HapticScapeEvent event)
	{
		send(new TransportMessage.Event(requireSource(event)));
	}

	private void seed(HapticScapeEvent event)
	{
		send(new TransportMessage.State(requireSource(event)));
	}

	private HapticScapeEvent requireSource(HapticScapeEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (!source.equals(event.getSource()))
		{
			throw new TransportProtocolException(
				"Event source does not match transport source: " + event.getSource()
			);
		}
		return event;
	}

	private void send(TransportMessage message)
	{
		TransportMessage decoded = codec.decode(codec.encode(message));
		TransportMessage response = receiver.receive(decoded);
		if (response != null)
		{
			TransportMessage roundTrippedResponse = codec.decode(codec.encode(response));
			throw rejection(roundTrippedResponse, "Transport rejected message");
		}
	}

	private TransportMessage roundTripResponse(TransportMessage message)
	{
		TransportMessage decoded = codec.decode(codec.encode(message));
		TransportMessage response = receiver.receive(decoded);
		if (response == null)
		{
			throw new TransportProtocolException("Transport control message produced no response");
		}
		return codec.decode(codec.encode(response));
	}

	private static TransportProtocolException rejection(
		TransportMessage response,
		String fallback)
	{
		if (response instanceof TransportMessage.Error)
		{
			TransportMessage.Error error = (TransportMessage.Error) response;
			return new TransportProtocolException(
				"Transport error [" + error.getCode() + "]: " + error.getMessage()
			);
		}
		return new TransportProtocolException(fallback);
	}
}
