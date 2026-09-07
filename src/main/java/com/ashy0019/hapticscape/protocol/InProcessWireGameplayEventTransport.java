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

/**
 * In-process proof transport that deliberately round-trips every gameplay
 * event through the versioned wire codec before delivering it to core.
 *
 * <p>This keeps the application in one JVM while exercising the exact event
 * serialization and generic dispatch boundary that a later localhost
 * transport will use.</p>
 */
public final class InProcessWireGameplayEventTransport implements GameplayEventSink
{
	private final EventWireCodec codec;
	private final GameplayEventDispatcher dispatcher;

	public InProcessWireGameplayEventTransport(
		EventWireCodec codec,
		GameplayEventSink downstream)
	{
		this.codec = Objects.requireNonNull(codec, "codec");
		this.dispatcher = new GameplayEventDispatcher(
			Objects.requireNonNull(downstream, "downstream")
		);
	}

	@Override
	public void resetSourceState()
	{
		// Source lifecycle is a transport control signal rather than a gameplay
		// event. Phase 3C will give control signals their localhost framing.
		dispatcher.resetSourceState();
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

	private void publish(HapticScapeEvent event)
	{
		dispatcher.publish(roundTrip(event));
	}

	private void seed(HapticScapeEvent event)
	{
		dispatcher.seed(roundTrip(event));
	}

	private HapticScapeEvent roundTrip(HapticScapeEvent event)
	{
		Objects.requireNonNull(event, "event");
		return codec.decode(codec.encode(event));
	}
}
