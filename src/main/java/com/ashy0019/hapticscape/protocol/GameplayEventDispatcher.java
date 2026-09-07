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

/** Routes decoded wire events into the source-neutral gameplay sink. */
public final class GameplayEventDispatcher
{
	private final GameplayEventSink sink;

	public GameplayEventDispatcher(GameplayEventSink sink)
	{
		this.sink = Objects.requireNonNull(sink, "sink");
	}

	public void publish(HapticScapeEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (event instanceof XpEvent)
		{
			sink.onXpEvent((XpEvent) event);
		}
		else if (event instanceof ChatEvent)
		{
			sink.onChatEvent((ChatEvent) event);
		}
		else if (event instanceof VitalsChangedEvent)
		{
			sink.onVitalsEvent((VitalsChangedEvent) event);
		}
		else if (event instanceof InventoryChangedEvent)
		{
			sink.onInventoryEvent((InventoryChangedEvent) event);
		}
		else if (event instanceof ToxicStatusChangedEvent)
		{
			sink.onToxicStatusEvent((ToxicStatusChangedEvent) event);
		}
		else if (event instanceof LootReceivedEvent)
		{
			sink.onLootEvent((LootReceivedEvent) event);
		}
		else if (event instanceof PlayerDeathEvent)
		{
			sink.onPlayerDeathEvent((PlayerDeathEvent) event);
		}
		else if (event instanceof NotificationEvent)
		{
			sink.onNotificationEvent((NotificationEvent) event);
		}
		else
		{
			throw unsupported(event, "published");
		}
	}

	public void seed(HapticScapeEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (event instanceof VitalsChangedEvent)
		{
			sink.seedVitals((VitalsChangedEvent) event);
		}
		else if (event instanceof InventoryChangedEvent)
		{
			sink.seedInventory((InventoryChangedEvent) event);
		}
		else if (event instanceof ToxicStatusChangedEvent)
		{
			sink.seedToxicStatus((ToxicStatusChangedEvent) event);
		}
		else
		{
			throw unsupported(event, "seeded");
		}
	}

	public void resetSourceState()
	{
		sink.resetSourceState();
	}

	private static EventProtocolException unsupported(HapticScapeEvent event, String operation)
	{
		return new EventProtocolException(
			"Event type cannot be " + operation + ": " + event.getClass().getName()
		);
	}
}
