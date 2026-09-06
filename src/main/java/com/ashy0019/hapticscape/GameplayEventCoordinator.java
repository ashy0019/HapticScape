package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.event.XpEvent;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteXpEventAdapter;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.runelite.api.Actor;
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
import net.runelite.client.util.Text;

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
	private final XpTracker xpTracker = new XpTracker();
	private final RuneLiteXpEventAdapter xpEventAdapter = new RuneLiteXpEventAdapter();
	private final ThresholdAlertTracker thresholdAlertTracker = new ThresholdAlertTracker();
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
		handleThresholdAlert(event);

		XpChange change = xpTracker.update(event.getSkill(), event.getXp());
		XpEvent xpEvent = xpEventAdapter.from(change);
		RemoteSettingsSnapshot settings = settingsSupplier.get();
		XpFeedbackSettings skillSettings = settings.getXpFeedbackSettings(change.getSkill());
		XpOutputDecision decision = XpOutputDecision.classify(
			xpEvent,
			settings.isHapticSkillEnabled(change.getSkill()),
			skillSettings,
			settings.isLevelUpFeedbackEnabled(),
			settings.isMilestoneFeedbackEnabled(),
			settings.isLevel99CelebrationEnabled(),
			settings.isClickSkillEnabled(change.getSkill()),
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
		boolean phraseClick = shouldClickForPhrase(event.getMessage());
		switch (event.getType())
		{
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				feedback.dispatchSpecificAlert(AlertCategory.DIRECT_MESSAGE, !phraseClick);
				break;
			case TRADEREQ:
				if (event.getMessage().contains("wishes to trade with you."))
				{
					feedback.dispatchSpecificAlert(AlertCategory.TRADE_REQUEST, !phraseClick);
				}
				break;
			default:
				break;
		}

		if (phraseClick)
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
		else if (event.getVarpId() == VarPlayerID.SA_ENERGY)
		{
			int energyPercent = clamp(event.getValue() / 10, 0, 100);
			int readyAt = settingsSupplier.get().getAlertTriggerSettings()
				.get(AlertCategory.SPECIAL_ATTACK_READY);
			if (thresholdAlertTracker.update(
				AlertCategory.SPECIAL_ATTACK_READY,
				energyPercent,
				readyAt
			))
			{
				dispatchSpecificAlert(AlertCategory.SPECIAL_ATTACK_READY);
			}
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
		Actor localPlayer = client.getLocalPlayer();
		if (localPlayer != null && event.getActor() == localPlayer)
		{
			dispatchSpecificAlert(AlertCategory.PLAYER_DEATH);
		}
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
			xpTracker.seed(skill, client.getSkillExperience(skill));
		}
	}

	private void seedAlertDetectors()
	{
		thresholdAlertTracker.seed(
			AlertCategory.LOW_HITPOINTS,
			client.getBoostedSkillLevel(Skill.HITPOINTS)
		);
		thresholdAlertTracker.seed(
			AlertCategory.LOW_PRAYER,
			client.getBoostedSkillLevel(Skill.PRAYER)
		);
		thresholdAlertTracker.seed(
			AlertCategory.SPECIAL_ATTACK_READY,
			clamp(client.getVarpValue(VarPlayerID.SA_ENERGY) / 10, 0, 100)
		);

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
		thresholdAlertTracker.reset();
		alertDeduplicator.reset();
		inventoryFullKnown = false;
		inventoryFull = false;
		poisonState = -1;
	}

	private void handleThresholdAlert(StatChanged event)
	{
		AlertCategory category;
		if (event.getSkill() == Skill.HITPOINTS)
		{
			category = AlertCategory.LOW_HITPOINTS;
		}
		else if (event.getSkill() == Skill.PRAYER)
		{
			category = AlertCategory.LOW_PRAYER;
		}
		else
		{
			return;
		}

		int threshold = settingsSupplier.get().getAlertTriggerSettings().get(category);
		if (thresholdAlertTracker.update(category, event.getBoostedLevel(), threshold))
		{
			dispatchSpecificAlert(category);
		}
	}

	private boolean shouldClickForPhrase(String rawMessage)
	{
		if (rawMessage == null)
		{
			return false;
		}
		String message = Text.unescapeJagex(rawMessage)
			.replace('\u00A0', ' ')
			.trim();
		return !message.isEmpty()
			&& settingsSupplier.get().getClickerPhraseRules().matches(message);
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

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
