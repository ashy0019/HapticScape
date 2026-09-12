package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Fans one neutral gameplay stream out to independent core consumers. */
final class CompositeGameplayEventSink implements GameplayEventSink
{
	private final List<GameplayEventSink> delegates;

	CompositeGameplayEventSink(GameplayEventSink... delegates)
	{
		Objects.requireNonNull(delegates, "delegates");
		this.delegates = Arrays.asList(delegates.clone());
		for (GameplayEventSink delegate : this.delegates)
		{
			Objects.requireNonNull(delegate, "delegate");
		}
	}

	@Override
	public void resetSourceState()
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.resetSourceState();
		}
	}

	@Override
	public void seedVitals(VitalsChangedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.seedVitals(event);
		}
	}

	@Override
	public void seedInventory(InventoryChangedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.seedInventory(event);
		}
	}

	@Override
	public void seedToxicStatus(ToxicStatusChangedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.seedToxicStatus(event);
		}
	}

	@Override
	public void onXpEvent(XpEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onXpEvent(event);
		}
	}

	@Override
	public void onChatEvent(ChatEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onChatEvent(event);
		}
	}

	@Override
	public void onVitalsEvent(VitalsChangedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onVitalsEvent(event);
		}
	}

	@Override
	public void onInventoryEvent(InventoryChangedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onInventoryEvent(event);
		}
	}

	@Override
	public void onToxicStatusEvent(ToxicStatusChangedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onToxicStatusEvent(event);
		}
	}

	@Override
	public void onLootEvent(LootReceivedEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onLootEvent(event);
		}
	}

	@Override
	public void onPlayerDeathEvent(PlayerDeathEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onPlayerDeathEvent(event);
		}
	}

	@Override
	public void onNotificationEvent(NotificationEvent event)
	{
		for (GameplayEventSink delegate : delegates)
		{
			delegate.onNotificationEvent(event);
		}
	}
}
