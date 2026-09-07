package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.InventoryChangedEvent;
import com.ashy0019.hapticscape.event.LootReceivedEvent;
import com.ashy0019.hapticscape.event.NotificationEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.ToxicStatusChangedEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import com.ashy0019.hapticscape.event.XpEventTracker;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteChatEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteInventoryEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteLootEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLitePlayerDeathEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteToxicStatusEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteVitalsEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteXpEventAdapter;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/** Tracks RuneLite gameplay state and translates raw events into feedback decisions. */
final class GameplayEventCoordinator implements AutoCloseable
{
	interface FeedbackSink
	{
		void handleXp(
			XpEvent event,
			XpOutputDecision decision,
			RemoteSettingsSnapshot settings,
			XpFeedbackSettings skillSettings);

		void dispatchSpecificAlert(AlertCategory category, boolean allowClick);

		void dispatchGenericAlert();

		void playClick();
	}

	private final Client client;
	private final Supplier<RemoteSettingsSnapshot> settingsSupplier;
	private final FeedbackSink feedback;
	private final XpEventTracker xpTracker = new XpEventTracker();
	private final RuneLiteXpEventAdapter xpEventAdapter = new RuneLiteXpEventAdapter();
	private final RuneLiteChatEventAdapter chatEventAdapter = new RuneLiteChatEventAdapter();
	private final RuneLiteInventoryEventAdapter inventoryEventAdapter =
		new RuneLiteInventoryEventAdapter();
	private final RuneLiteLootEventAdapter lootEventAdapter;
	private final RuneLitePlayerDeathEventAdapter playerDeathEventAdapter =
		new RuneLitePlayerDeathEventAdapter();
	private final RuneLiteToxicStatusEventAdapter toxicStatusEventAdapter =
		new RuneLiteToxicStatusEventAdapter();
	private final RuneLiteVitalsEventAdapter vitalsEventAdapter =
		new RuneLiteVitalsEventAdapter();
	private final VitalsAlertTracker vitalsAlertTracker = new VitalsAlertTracker();
	private final InventoryAlertTracker inventoryAlertTracker = new InventoryAlertTracker();
	private final ToxicStatusAlertTracker toxicStatusAlertTracker = new ToxicStatusAlertTracker();
	private final AlertDeduplicator alertDeduplicator = new AlertDeduplicator();

	private ScheduledExecutorService alertScheduler;

	GameplayEventCoordinator(
		Client client,
		ItemManager itemManager,
		Supplier<RemoteSettingsSnapshot> settingsSupplier,
		FeedbackSink feedback)
	{
		this.client = Objects.requireNonNull(client, "client");
		this.lootEventAdapter = new RuneLiteLootEventAdapter(
			Objects.requireNonNull(itemManager, "itemManager")
		);
		this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	void start()
	{
		resetTrackers();
		alertScheduler = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-alerts");
			thread.setDaemon(true);
			return thread;
		});
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			seedCurrentXp();
			seedAlertDetectors();
		}
	}

	void onGameStateChanged(GameState gameState)
	{
		if (gameState == GameState.LOGGED_IN)
		{
			seedCurrentXp();
			seedAlertDetectors();
		}
		else if (gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.HOPPING
			|| gameState == GameState.CONNECTION_LOST)
		{
			resetTrackers();
		}
	}

	void onStatChanged(StatChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		vitalsEventAdapter.adapt(event).ifPresent(this::handleVitalsChangedEvent);

		Skill skill = event.getSkill();
		XpEvent xpEvent = xpEventAdapter.update(xpTracker, skill, event.getXp());
		RemoteSettingsSnapshot settings = settingsSupplier.get();
		XpFeedbackSettings skillSettings = settings.getXpFeedbackSettings(skill);
		XpOutputDecision decision = XpOutputDecision.classify(
			xpEvent,
			settings.isHapticSkillEnabled(skill),
			skillSettings,
			settings.isLevelUpFeedbackEnabled(),
			settings.isMilestoneFeedbackEnabled(),
			settings.isLevel99CelebrationEnabled(),
			settings.isClickSkillEnabled(skill),
			settings.getClickerXpSettings()
		);
		if (decision.shouldClick())
		{
			feedback.playClick();
		}

		feedback.handleXp(xpEvent, decision, settings, skillSettings);
	}

	void onChatMessage(ChatMessage event)
	{
		handleChatEvent(chatEventAdapter.adapt(event));
	}

	void handleChatEvent(ChatEvent event)
	{
		Objects.requireNonNull(event, "event");
		ChatOutputDecision decision = ChatOutputDecision.classify(
			event,
			settingsSupplier.get().getClickerPhraseRules()
		);
		if (decision.hasSpecificAlert())
		{
			feedback.dispatchSpecificAlert(
				decision.getSpecificAlert(),
				!decision.shouldClick()
			);
		}

		if (decision.shouldClick())
		{
			feedback.playClick();
		}
	}

	void onItemContainerChanged(ItemContainerChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		inventoryEventAdapter.adapt(event).ifPresent(this::handleInventoryChangedEvent);
	}

	void onVarbitChanged(VarbitChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		vitalsEventAdapter.adapt(event).ifPresent(this::handleVitalsChangedEvent);
		toxicStatusEventAdapter.adapt(event).ifPresent(this::handleToxicStatusChangedEvent);
	}

	void onLootReceived(Collection<ItemStack> items)
	{
		if (items == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		handleLootReceivedEvent(lootEventAdapter.adapt(items));
	}

	void handleLootReceivedEvent(LootReceivedEvent event)
	{
		Objects.requireNonNull(event, "event");
		long minimumValue = settingsSupplier.get().getAlertTriggerSettings()
			.get(AlertCategory.VALUABLE_DROP);
		if (LootAlertDecision.shouldAlert(event, minimumValue))
		{
			dispatchSpecificAlert(AlertCategory.VALUABLE_DROP);
		}
	}

	void onActorDeath(ActorDeath event)
	{
		playerDeathEventAdapter
			.adapt(event, client.getLocalPlayer())
			.ifPresent(this::handlePlayerDeathEvent);
	}

	void handlePlayerDeathEvent(PlayerDeathEvent event)
	{
		Objects.requireNonNull(event, "event");
		dispatchSpecificAlert(AlertCategory.PLAYER_DEATH);
	}

	void onNotificationEvent(NotificationEvent event)
	{
		Objects.requireNonNull(event, "event");
		RemoteSettingsSnapshot effective = settingsSupplier.get();
		NotificationFeedbackSettings settings = effective.getNotificationFeedbackSettings();
		if (!GenericNotificationDecision.shouldDispatch(
			event,
			settings,
			effective.isGenericNotificationClickEnabled()
		))
		{
			return;
		}

		ScheduledExecutorService scheduler = alertScheduler;
		if (scheduler == null)
		{
			return;
		}
		long notificationNanos = System.nanoTime();
		try
		{
			scheduler.schedule(
				() ->
				{
					if (!alertDeduplicator.shouldSuppressGeneric(
						notificationNanos,
						System.nanoTime()
					))
					{
						feedback.dispatchGenericAlert();
					}
				},
				AlertDeduplicator.GENERIC_DELAY_MILLIS,
				TimeUnit.MILLISECONDS
			);
		}
		catch (RejectedExecutionException ignored)
		{
			// Plugin shutdown won the race with this notification.
		}
	}

	@Override
	public void close()
	{
		ScheduledExecutorService scheduler = alertScheduler;
		alertScheduler = null;
		if (scheduler != null)
		{
			scheduler.shutdownNow();
		}
		resetTrackers();
	}

	private void seedCurrentXp()
	{
		for (Skill skill : Skill.values())
		{
			xpEventAdapter.seed(xpTracker, skill, client.getSkillExperience(skill));
		}
	}

	private void seedAlertDetectors()
	{
		vitalsAlertTracker.seed(vitalsEventAdapter.hitpoints(
			client.getBoostedSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.HITPOINTS)
		));
		vitalsAlertTracker.seed(vitalsEventAdapter.prayer(
			client.getBoostedSkillLevel(Skill.PRAYER),
			client.getRealSkillLevel(Skill.PRAYER)
		));
		vitalsAlertTracker.seed(vitalsEventAdapter.specialAttackFromVarp(
			client.getVarpValue(VarPlayerID.SA_ENERGY)
		));

		toxicStatusAlertTracker.seed(toxicStatusEventAdapter.fromVarp(
			client.getVarpValue(VarPlayerID.POISON)
		));
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			inventoryAlertTracker.seed(inventoryEventAdapter.inventory(inventory));
		}
	}

	private void resetTrackers()
	{
		xpTracker.reset();
		vitalsAlertTracker.reset();
		inventoryAlertTracker.reset();
		toxicStatusAlertTracker.reset();
		alertDeduplicator.reset();
	}

	void handleVitalsChangedEvent(VitalsChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		vitalsAlertTracker.update(
			event,
			settingsSupplier.get().getAlertTriggerSettings()
		).ifPresent(this::dispatchSpecificAlert);
	}


	void handleInventoryChangedEvent(InventoryChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		inventoryAlertTracker.update(event).ifPresent(this::dispatchSpecificAlert);
	}

	void handleToxicStatusChangedEvent(ToxicStatusChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		toxicStatusAlertTracker.update(event).ifPresent(this::dispatchSpecificAlert);
	}

	private void dispatchSpecificAlert(AlertCategory category)
	{
		dispatchSpecificAlert(category, true);
	}

	private void dispatchSpecificAlert(AlertCategory category, boolean allowClick)
	{
		alertDeduplicator.recordSpecificAlert(System.nanoTime());
		feedback.dispatchSpecificAlert(category, allowClick);
	}

}
