package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.util.Objects;

/** Adds runtime-owned reset behavior around an otherwise neutral gameplay sink. */
final class ResetAwareGameplayEventSink implements GameplayEventSink
{
	private final GameplayEventSink delegate;
	private final Runnable onReset;

	ResetAwareGameplayEventSink(GameplayEventSink delegate, Runnable onReset)
	{
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.onReset = Objects.requireNonNull(onReset, "onReset");
	}

	@Override
	public void resetSourceState()
	{
		delegate.resetSourceState();
		onReset.run();
	}

	@Override
	public void seedVitals(VitalsChangedEvent event)
	{
		delegate.seedVitals(event);
	}

	@Override
	public void seedInventory(InventoryChangedEvent event)
	{
		delegate.seedInventory(event);
	}

	@Override
	public void seedToxicStatus(ToxicStatusChangedEvent event)
	{
		delegate.seedToxicStatus(event);
	}

	@Override
	public void onXpEvent(XpEvent event)
	{
		delegate.onXpEvent(event);
	}

	@Override
	public void onChatEvent(ChatEvent event)
	{
		delegate.onChatEvent(event);
	}

	@Override
	public void onVitalsEvent(VitalsChangedEvent event)
	{
		delegate.onVitalsEvent(event);
	}

	@Override
	public void onInventoryEvent(InventoryChangedEvent event)
	{
		delegate.onInventoryEvent(event);
	}

	@Override
	public void onToxicStatusEvent(ToxicStatusChangedEvent event)
	{
		delegate.onToxicStatusEvent(event);
	}

	@Override
	public void onLootEvent(LootReceivedEvent event)
	{
		delegate.onLootEvent(event);
	}

	@Override
	public void onPlayerDeathEvent(PlayerDeathEvent event)
	{
		delegate.onPlayerDeathEvent(event);
	}

	@Override
	public void onNotificationEvent(NotificationEvent event)
	{
		delegate.onNotificationEvent(event);
	}
}
