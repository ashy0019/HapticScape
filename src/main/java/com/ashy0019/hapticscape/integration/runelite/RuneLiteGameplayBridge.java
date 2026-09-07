package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.GameplayEventSink;
import com.ashy0019.hapticscape.event.XpEventTracker;
import java.util.Collection;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/**
 * RuneLite-facing gameplay bridge. All RuneLite objects are translated to
 * source-neutral HapticScape events before reaching the core event sink.
 */
public final class RuneLiteGameplayBridge
{
	private final Client client;
	private final GameplayEventSink sink;
	private final XpEventTracker xpTracker = new XpEventTracker();
	private final RuneLiteXpEventAdapter xpEventAdapter = new RuneLiteXpEventAdapter();
	private final RuneLiteChatEventAdapter chatEventAdapter = new RuneLiteChatEventAdapter();
	private final RuneLiteInventoryEventAdapter inventoryEventAdapter =
		new RuneLiteInventoryEventAdapter();
	private final RuneLiteLootEventAdapter lootEventAdapter;
	private final RuneLiteNotificationEventAdapter notificationEventAdapter =
		new RuneLiteNotificationEventAdapter();
	private final RuneLitePlayerDeathEventAdapter playerDeathEventAdapter =
		new RuneLitePlayerDeathEventAdapter();
	private final RuneLiteToxicStatusEventAdapter toxicStatusEventAdapter =
		new RuneLiteToxicStatusEventAdapter();
	private final RuneLiteVitalsEventAdapter vitalsEventAdapter =
		new RuneLiteVitalsEventAdapter();

	public RuneLiteGameplayBridge(Client client, ItemManager itemManager, GameplayEventSink sink)
	{
		this.client = Objects.requireNonNull(client, "client");
		this.lootEventAdapter = new RuneLiteLootEventAdapter(
			Objects.requireNonNull(itemManager, "itemManager")
		);
		this.sink = Objects.requireNonNull(sink, "sink");
	}

	public void start()
	{
		xpTracker.reset();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			seedCurrentState();
		}
	}

	public void onGameStateChanged(GameState gameState)
	{
		Objects.requireNonNull(gameState, "gameState");
		if (gameState == GameState.LOGGED_IN)
		{
			seedCurrentState();
		}
		else if (gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.HOPPING
			|| gameState == GameState.CONNECTION_LOST)
		{
			xpTracker.reset();
			sink.resetSourceState();
		}
	}

	public void onStatChanged(StatChanged event)
	{
		if (!isLoggedIn())
		{
			return;
		}

		vitalsEventAdapter.adapt(event).ifPresent(sink::onVitalsEvent);
		sink.onXpEvent(
			xpEventAdapter.update(xpTracker, event.getSkill(), event.getXp())
		);
	}

	public void onChatMessage(ChatMessage event)
	{
		sink.onChatEvent(chatEventAdapter.adapt(event));
	}

	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!isLoggedIn())
		{
			return;
		}
		inventoryEventAdapter.adapt(event).ifPresent(sink::onInventoryEvent);
	}

	public void onVarbitChanged(VarbitChanged event)
	{
		if (!isLoggedIn())
		{
			return;
		}
		vitalsEventAdapter.adapt(event).ifPresent(sink::onVitalsEvent);
		toxicStatusEventAdapter.adapt(event).ifPresent(sink::onToxicStatusEvent);
	}

	public void onLootReceived(Collection<ItemStack> items)
	{
		if (items == null || !isLoggedIn())
		{
			return;
		}
		sink.onLootEvent(lootEventAdapter.adapt(items));
	}

	public void onActorDeath(ActorDeath event)
	{
		playerDeathEventAdapter
			.adapt(event, client.getLocalPlayer())
			.ifPresent(sink::onPlayerDeathEvent);
	}

	public void onNotificationFired(NotificationFired event, boolean sourceFocused)
	{
		sink.onNotificationEvent(notificationEventAdapter.adapt(event, sourceFocused));
	}

	private boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	private void seedCurrentState()
	{
		for (Skill skill : Skill.values())
		{
			xpEventAdapter.seed(xpTracker, skill, client.getSkillExperience(skill));
		}

		sink.seedVitals(vitalsEventAdapter.hitpoints(
			client.getBoostedSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.HITPOINTS)
		));
		sink.seedVitals(vitalsEventAdapter.prayer(
			client.getBoostedSkillLevel(Skill.PRAYER),
			client.getRealSkillLevel(Skill.PRAYER)
		));
		sink.seedVitals(vitalsEventAdapter.specialAttackFromVarp(
			client.getVarpValue(VarPlayerID.SA_ENERGY)
		));

		sink.seedToxicStatus(toxicStatusEventAdapter.fromVarp(
			client.getVarpValue(VarPlayerID.POISON)
		));

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			sink.seedInventory(inventoryEventAdapter.inventory(inventory));
		}
	}
}
