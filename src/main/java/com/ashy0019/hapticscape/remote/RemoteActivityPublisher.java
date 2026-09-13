package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Converts neutral gameplay observations into the small Remote Play activity allowlist. */
public final class RemoteActivityPublisher implements GameplayEventSink
{
	private final Consumer<RemoteActivityEvent> sink;
	private final Clock clock;
	private boolean inventoryFull;

	public RemoteActivityPublisher(Consumer<RemoteActivityEvent> sink)
	{
		this(sink, Clock.systemUTC());
	}

	RemoteActivityPublisher(Consumer<RemoteActivityEvent> sink, Clock clock)
	{
		this.sink = Objects.requireNonNull(sink, "sink");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public void resetSourceState()
	{
		inventoryFull = false;
	}

	@Override
	public void seedVitals(VitalsChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
	}

	@Override
	public void seedInventory(InventoryChangedEvent event)
	{
		inventoryFull = Objects.requireNonNull(event, "event").isFull();
	}

	@Override
	public void seedToxicStatus(ToxicStatusChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
	}

	@Override
	public void onXpEvent(XpEvent event)
	{
		Objects.requireNonNull(event, "event");
		String skill = displayIdentifier(event.getSkillId());
		String gain = "+" + event.getGainedXp() + " XP";
		if (event.crossedLevel(99))
		{
			publish(RemoteActivityType.MILESTONE, skill, "Level 99 • " + gain);
		}
		else if (event.crossedDecadeMilestone())
		{
			publish(RemoteActivityType.MILESTONE, skill, "Level " + event.getCurrentLevel() + " • " + gain);
		}
		else if (event.isLevelUp())
		{
			publish(RemoteActivityType.LEVEL_UP, skill, "Level " + event.getCurrentLevel() + " • " + gain);
		}
		else if (event.getGainedXp() > 0)
		{
			publish(RemoteActivityType.XP_GAIN, skill, gain);
		}
	}

	@Override
	public void onChatEvent(ChatEvent event)
	{
		Objects.requireNonNull(event, "event");
		if (event.getKind() == ChatEvent.Kind.TRADE_REQUEST)
		{
			publish(RemoteActivityType.TRADE_REQUEST, "Trade request", "Received");
		}
		else if (event.getKind() == ChatEvent.Kind.DIRECT_MESSAGE)
		{
			// Deliberately report only the fact that a message arrived. Raw chat text
			// never enters the remote activity protocol.
			publish(RemoteActivityType.DIRECT_MESSAGE, "Direct message", "Received");
		}
	}

	@Override
	public void onVitalsEvent(VitalsChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		publish(
			RemoteActivityType.RESOURCE_CHANGED,
			displayIdentifier(event.getKind().name()),
			event.getCurrentValue() + " / " + event.getMaximumValue()
		);
	}

	@Override
	public void onInventoryEvent(InventoryChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		boolean nowFull = event.isFull();
		if (nowFull && !inventoryFull)
		{
			publish(
				RemoteActivityType.INVENTORY_FULL,
				"Inventory Full",
				""
			);
		}
		inventoryFull = nowFull;
	}

	@Override
	public void onToxicStatusEvent(ToxicStatusChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		publish(
			RemoteActivityType.STATUS_CHANGED,
			"Status",
			displayIdentifier(event.getStatus().name())
		);
	}

	@Override
	public void onLootEvent(LootReceivedEvent event)
	{
		Objects.requireNonNull(event, "event");
		String stacks = event.getStackCount() == 1 ? "1 stack" : event.getStackCount() + " stacks";
		publish(
			RemoteActivityType.LOOT_RECEIVED,
			"Loot",
			String.format(Locale.ROOT, "%,d value • %s", event.getTotalValue(), stacks)
		);
	}

	@Override
	public void onPlayerDeathEvent(PlayerDeathEvent event)
	{
		Objects.requireNonNull(event, "event");
		publish(RemoteActivityType.PLAYER_DEATH, "Player", "Died");
	}

	@Override
	public void onNotificationEvent(NotificationEvent event)
	{
		// Generic source notifications intentionally stay local. They do not add
		// enough gameplay context to justify another remotely visible fact.
		Objects.requireNonNull(event, "event");
	}

	private void publish(RemoteActivityType type, String label, String detail)
	{
		sink.accept(new RemoteActivityEvent(type, label, detail, clock.millis()));
	}

	private static String displayIdentifier(String value)
	{
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
			.replace('_', ' ')
			.replace('-', ' ');
		if (normalized.isEmpty())
		{
			return "Activity";
		}
		StringBuilder output = new StringBuilder(normalized.length());
		boolean upper = true;
		for (int index = 0; index < normalized.length(); index++)
		{
			char current = normalized.charAt(index);
			if (upper && Character.isLetter(current))
			{
				output.append(Character.toUpperCase(current));
				upper = false;
			}
			else
			{
				output.append(current);
				upper = current == ' ';
			}
		}
		return output.toString();
	}
}
