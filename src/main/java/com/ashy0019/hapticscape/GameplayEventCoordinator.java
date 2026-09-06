package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.ChatEvent;
import com.ashy0019.hapticscape.event.PlayerDeathEvent;
import com.ashy0019.hapticscape.event.VitalsChangedEvent;
import com.ashy0019.hapticscape.event.XpEvent;
import com.ashy0019.hapticscape.event.XpEventTracker;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteChatEventAdapter;
import com.ashy0019.hapticscape.integration.runelite.RuneLitePlayerDeathEventAdapter;
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
import net.runelite.client.events.NotificationFired;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.ClientUI;

/** Tracks RuneLite gameplay state and translates raw events into feedback decisions. */
final class GameplayEventCoordinator implements AutoCloseable
{
	private static final int VENOM_THRESHOLD = 1_000_000;

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
	private final ClientUI clientUi;
	private final ItemManager itemManager;
	private final Supplier<RemoteSettingsSnapshot> settingsSupplier;
	private final FeedbackSink feedback;
	private final XpEventTracker xpTracker = new XpEventTracker();
	private final RuneLiteXpEventAdapter xpEventAdapter = new RuneLiteXpEventAdapter();
	private final RuneLiteChatEventAdapter chatEventAdapter = new RuneLiteChatEventAdapter();
	private final RuneLitePlayerDeathEventAdapter playerDeathEventAdapter =
		new RuneLitePlayerDeathEventAdapter();
	private final RuneLiteVitalsEventAdapter vitalsEventAdapter =
		new RuneLiteVitalsEventAdapter();
	private final VitalsAlertTracker vitalsAlertTracker = new VitalsAlertTracker();
	private final AlertDeduplicator alertDeduplicator = new AlertDeduplicator();

	private ScheduledExecutorService alertScheduler;
	private boolean inventoryFullKnown;
	private boolean inventoryFull;
	private int poisonState = -1;

	GameplayEventCoordinator(
		Client client,
		ClientUI clientUi,
		ItemManager itemManager,
		Supplier<RemoteSettingsSnapshot> settingsSupplier,
		FeedbackSink feedback)
	{
		this.client = Objects.requireNonNull(client, "client");
		this.clientUi = Objects.requireNonNull(clientUi, "clientUi");
		this.itemManager = Objects.requireNonNull(itemManager, "itemManager");
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
		if (client.getGameState() != GameState.LOGGED_IN
			|| event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		ItemContainer inventory = event.getItemContainer();
		boolean full = inventory.size() > 0 && inventory.count() >= inventory.size();
		if (inventoryFullKnown && !inventoryFull && full)
		{
			dispatchSpecificAlert(AlertCategory.INVENTORY_FULL);
		}
		inventoryFullKnown = true;
		inventoryFull = full;
	}

	void onVarbitChanged(VarbitChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		vitalsEventAdapter.adapt(event).ifPresent(this::handleVitalsChangedEvent);

		if (event.getVarpId() == VarPlayerID.POISON)
		{
			int currentPoisonState = classifyPoisonState(event.getValue());
			boolean newlyAffected = poisonState == 0 && currentPoisonState > 0;
			boolean newlyEnvenomed = poisonState == 1 && currentPoisonState == 2;
			if (poisonState >= 0 && (newlyAffected || newlyEnvenomed))
			{
				dispatchSpecificAlert(AlertCategory.POISONED_OR_VENOMED);
			}
			poisonState = currentPoisonState;
		}
	}

	void onLootReceived(Collection<ItemStack> items)
	{
		if (items == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long minimumValue = settingsSupplier.get().getAlertTriggerSettings()
			.get(AlertCategory.VALUABLE_DROP);
		long totalValue = 0;
		for (ItemStack item : items)
		{
			int unitPrice = Math.max(0, itemManager.getItemPrice(item.getId()));
			int quantity = Math.max(0, item.getQuantity());
			totalValue += (long) unitPrice * quantity;
			if (totalValue >= minimumValue)
			{
				dispatchSpecificAlert(AlertCategory.VALUABLE_DROP);
				return;
			}
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

	void onNotificationFired(NotificationFired event)
	{
		RemoteSettingsSnapshot effective = settingsSupplier.get();
		NotificationFeedbackSettings settings = effective.getNotificationFeedbackSettings();
		if (!GenericNotificationDecision.shouldDispatch(
			settings,
			effective.isGenericNotificationClickEnabled(),
			clientUi.isFocused(),
			event.getNotification().isSendWhenFocused()
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

		poisonState = classifyPoisonState(client.getVarpValue(VarPlayerID.POISON));
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		inventoryFullKnown = inventory != null;
		inventoryFull = inventory != null
			&& inventory.size() > 0
			&& inventory.count() >= inventory.size();
	}

	private void resetTrackers()
	{
		xpTracker.reset();
		vitalsAlertTracker.reset();
		alertDeduplicator.reset();
		inventoryFullKnown = false;
		inventoryFull = false;
		poisonState = -1;
	}

	void handleVitalsChangedEvent(VitalsChangedEvent event)
	{
		Objects.requireNonNull(event, "event");
		vitalsAlertTracker.update(
			event,
			settingsSupplier.get().getAlertTriggerSettings()
		).ifPresent(this::dispatchSpecificAlert);
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

	static int classifyPoisonState(int poisonValue)
	{
		if (poisonValue <= 0)
		{
			return 0;
		}
		return poisonValue >= VENOM_THRESHOLD ? 2 : 1;
	}
}
