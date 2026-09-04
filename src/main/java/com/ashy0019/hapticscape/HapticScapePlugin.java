package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.clicker.AudioPlayerClickPlayback;
import com.ashy0019.hapticscape.clicker.ClickerService;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.device.DefaultIntifaceService;
import com.ashy0019.hapticscape.device.GatedIntifaceService;
import com.ashy0019.hapticscape.device.HapticEventType;
import com.ashy0019.hapticscape.device.HapticPattern;
import com.ashy0019.hapticscape.device.HapticRequest;
import com.ashy0019.hapticscape.device.IntifaceService;
import com.ashy0019.hapticscape.music.MusicResponse;
import com.ashy0019.hapticscape.music.MusicSyncService;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.music.WasapiLoopbackCapture;
import com.ashy0019.hapticscape.rogue.RogueFeedbackEvent;
import com.ashy0019.hapticscape.remote.ConfigBackedRemoteSettingsStore;
import com.ashy0019.hapticscape.remote.EffectiveSettingsService;
import com.ashy0019.hapticscape.remote.RemoteRole;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.RemoteSessionState;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.rogue.feedback.CasinoFeedbackMapper;
import com.ashy0019.hapticscape.ui.HapticScapePanel;
import com.ashy0019.hapticscape.ui.Level99CelebrationOverlay;
import com.ashy0019.hapticscape.update.UpdateCheckService;
import com.ashy0019.hapticscape.update.UpdatePreferencesStore;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "HapticScape",
	description = "Triggers configurable Intiface device feedback from game events",
	tags = {"intiface", "haptics", "feedback", "xp", "accessibility"}
)
public class HapticScapePlugin extends Plugin
{
	private static final String LEVEL_99_CHEER_RESOURCE = "/level99-cheer.wav";
	private static final float LEVEL_99_CHEER_GAIN_DB = -4.0f;
	private static final String ROGUE_UNLOCK_STING_RESOURCE = "/rogue/rogue-unlock.wav";
	private static final float ROGUE_UNLOCK_STING_GAIN_DB = -4.0f;
	private static final int VENOM_THRESHOLD = 1_000_000;

	private final XpTracker xpTracker = new XpTracker();
	private final ThresholdAlertTracker thresholdAlertTracker = new ThresholdAlertTracker();
	private final AlertDeduplicator alertDeduplicator = new AlertDeduplicator();
	private final Level99CelebrationController level99CelebrationController =
		new Level99CelebrationController();
	private GatedIntifaceService intifaceService;
	private EffectiveSettingsService effectiveSettingsService;
	private RemoteSessionManager remoteSessionManager;
	private SettingsLockService settingsLockService;
	private MusicSyncService musicSyncService;
	private ClickerService clickerService;
	private HapticScapePanel panel;
	private NavigationButton navigationButton;
	private Level99CelebrationOverlay level99CelebrationOverlay;
	private ScheduledExecutorService alertScheduler;
	private UpdatePreferencesStore updatePreferencesStore;
	private UpdateCheckService updateCheckService;
	private boolean inventoryFullKnown;
	private boolean inventoryFull;
	private int poisonState = -1;

	@Inject
	private Client client;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private HapticScapeConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientUI clientUI;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Override
	protected void startUp()
	{
		xpTracker.reset();
		resetAlertDetectors();
		alertDeduplicator.reset();
		alertScheduler = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "hapticscape-alerts");
			thread.setDaemon(true);
			return thread;
		});
		level99CelebrationController.reset();
		level99CelebrationOverlay = new Level99CelebrationOverlay(
			this,
			level99CelebrationController
		);
		overlayManager.add(level99CelebrationOverlay);

		intifaceService = new GatedIntifaceService(
			new DefaultIntifaceService(httpClient, gson)
		);
		effectiveSettingsService = new EffectiveSettingsService(config);
		settingsLockService = new SettingsLockService(gson);
		remoteSessionManager = new RemoteSessionManager(
			httpClient,
			gson,
			new ConfigBackedRemoteSettingsStore(config, configManager),
			effectiveSettingsService,
			settingsLockService
		);
		remoteSessionManager.addListener(new RemoteSessionListener()
		{
			@Override
			public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
			{
				handleRemoteSessionChanged(snapshot);
			}

			@Override
			public void onRemoteSettingsChanged(RemoteSettingsSnapshot settings)
			{
				handleRemoteSettingsChanged(settings);
			}
		});
		musicSyncService = new MusicSyncService(
			intifaceService,
			WasapiLoopbackCapture::new,
			musicSettingsFromConfig()
		);
		clickerService = new ClickerService(
			new AudioPlayerClickPlayback(audioPlayer),
			clickerSettingsFromConfig()
		);
		updatePreferencesStore = new UpdatePreferencesStore(gson);
		updateCheckService = new UpdateCheckService(httpClient, gson);
		panel = new HapticScapePanel(
			config,
			configManager,
			this::connectToIntiface,
			intifaceService::disconnect,
			this::sendTestPattern,
			this::sendTestLevelUpPattern,
			this::previewLevel99Ceremony,
			this::sendTestSkillProfile,
			this::sendTestGenericNotificationPattern,
			this::sendTestAlert,
			this::sendPatternForgePreview,
			musicSyncService::updateSettings,
			clickerService::updateSettings,
			clickerService::click,
			updatePreferencesStore,
			updateCheckService,
			remoteSessionManager,
			settingsLockService,
			this::dispatchRogueFeedback,
			this::playRogueUnlockStingAsync,
			intifaceService::stopAll
		);
		intifaceService.setConnectionListener(panel::updateConnection);
		musicSyncService.setListener(panel::updateMusicSync);
		musicSyncService.updateSettings(effectiveSettingsService.current().getMusicSyncSettings());
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			seedCurrentXp();
			seedAlertDetectors();
		}

		navigationButton = NavigationButton.builder()
			.tooltip("HapticScape")
			.icon(loadNavigationIcon())
			.panel(panel)
			.priority(5)
			.build();
		clientToolbar.addNavigation(navigationButton);

		log.info("HapticScape started");
	}

	@Override
	protected void shutDown()
	{
		xpTracker.reset();
		resetAlertDetectors();
		alertDeduplicator.reset();
		level99CelebrationController.reset();
		if (level99CelebrationOverlay != null)
		{
			overlayManager.remove(level99CelebrationOverlay);
			level99CelebrationOverlay = null;
		}

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		if (musicSyncService != null)
		{
			musicSyncService.setListener(snapshot -> { });
			musicSyncService.close();
			musicSyncService = null;
		}
		if (remoteSessionManager != null)
		{
			remoteSessionManager.close();
			remoteSessionManager = null;
		}
		effectiveSettingsService = null;
		if (clickerService != null)
		{
			clickerService.close();
			clickerService = null;
		}
		if (intifaceService != null)
		{
			intifaceService.setConnectionListener(snapshot -> { });
			intifaceService.close();
			intifaceService = null;
		}
		if (panel != null)
		{
			panel.close();
			panel = null;
		}
		settingsLockService = null;
		if (updateCheckService != null)
		{
			updateCheckService.close();
			updateCheckService = null;
		}
		if (updatePreferencesStore != null)
		{
			updatePreferencesStore.close();
			updatePreferencesStore = null;
		}
		if (alertScheduler != null)
		{
			ScheduledExecutorService scheduler = alertScheduler;
			alertScheduler = null;
			scheduler.shutdownNow();
		}

		log.info("HapticScape stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();

		if (gameState == GameState.LOGGED_IN)
		{
			seedCurrentXp();
			seedAlertDetectors();
		}
		else if (gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.HOPPING
			|| gameState == GameState.CONNECTION_LOST)
		{
			xpTracker.reset();
			resetAlertDetectors();
			alertDeduplicator.reset();
			level99CelebrationController.reset();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		handleThresholdAlert(event);

		XpChange change = xpTracker.update(event.getSkill(), event.getXp());
		RemoteSettingsSnapshot settings = effectiveSettings();
		XpFeedbackSettings skillXpSettings = settings.getXpFeedbackSettings(change.getSkill());
		XpOutputDecision decision = XpOutputDecision.classify(
			change,
			settings.isHapticSkillEnabled(change.getSkill()),
			skillXpSettings,
			settings.isLevelUpFeedbackEnabled(),
			settings.isMilestoneFeedbackEnabled(),
			settings.isLevel99CelebrationEnabled(),
			settings.isClickSkillEnabled(change.getSkill()),
			settings.getClickerXpSettings()
		);
		if (decision.shouldClick() && clickerService != null)
		{
			clickerService.click();
		}
		handleFeedbackTrigger(
			change,
			decision.getHapticTrigger(),
			settings,
			skillXpSettings
		);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		boolean phraseClick = shouldClickForPhrase(event.getMessage());

		switch (event.getType())
		{
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				dispatchSpecificAlert(
					AlertCategory.DIRECT_MESSAGE,
					!phraseClick
				);
				break;
			case TRADEREQ:
				if (event.getMessage().contains("wishes to trade with you."))
				{
					dispatchSpecificAlert(
						AlertCategory.TRADE_REQUEST,
						!phraseClick
					);
				}
				break;
			default:
				break;
		}

		if (phraseClick && clickerService != null)
		{
			log.debug("Phrase rule click requested");
			clickerService.click();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
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

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
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
			int readyAt = effectiveSettings().getAlertTriggerSettings()
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

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		handleValuableLoot(event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		handleValuableLoot(event.getItems());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor localPlayer = client.getLocalPlayer();
		if (localPlayer != null && event.getActor() == localPlayer)
		{
			dispatchSpecificAlert(AlertCategory.PLAYER_DEATH);
		}
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		RemoteSettingsSnapshot effective = effectiveSettings();
		NotificationFeedbackSettings settings =
			effective.getNotificationFeedbackSettings();
		boolean genericClickEnabled = effective.isGenericNotificationClickEnabled();
		if (!GenericNotificationDecision.shouldDispatch(
			settings,
			genericClickEnabled,
			clientUI.isFocused(),
			event.getNotification().isSendWhenFocused()))
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
						dispatchGenericAlert();
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
			clamp(
				client.getVarpValue(VarPlayerID.SA_ENERGY) / 10,
				0,
				100
			)
		);

		poisonState = classifyPoisonState(client.getVarpValue(VarPlayerID.POISON));
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		inventoryFullKnown = inventory != null;
		inventoryFull = inventory != null
			&& inventory.size() > 0
			&& inventory.count() >= inventory.size();
	}

	private void resetAlertDetectors()
	{
		thresholdAlertTracker.reset();
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

		int threshold = effectiveSettings().getAlertTriggerSettings().get(category);
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
			&& effectiveSettings().getClickerPhraseRules().matches(message);
	}

	private void dispatchSpecificAlert(AlertCategory category)
	{
		dispatchSpecificAlert(category, true);
	}

	private void dispatchSpecificAlert(
		AlertCategory category,
		boolean allowClick)
	{
		alertDeduplicator.recordSpecificAlert(System.nanoTime());
		dispatchAlert(category, allowClick);
	}

	private void dispatchGenericAlert()
	{
		RemoteSettingsSnapshot effective = effectiveSettings();
		NotificationFeedbackSettings settings =
			effective.getNotificationFeedbackSettings();
		if (effective.isGenericNotificationClickEnabled() && clickerService != null)
		{
			log.debug("Generic notification click requested");
			clickerService.click();
		}
		if (!settings.isEnabled())
		{
			return;
		}
		log.debug("Generic notification haptic requested");
		sendPattern(
			HapticEventType.GENERIC_NOTIFICATION,
			settings.getPatternSelection(),
			"ALERT_GENERIC_NOTIFICATION",
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void dispatchAlert(
		AlertCategory category,
		boolean allowClick)
	{
		RemoteSettingsSnapshot effective = effectiveSettings();

		if (allowClick
			&& effective.isAlertClickEnabled(category)
			&& clickerService != null)
		{
			log.debug("{} click requested", category);
			clickerService.click();
		}

		effective.getAlertProfiles()
			.resolve(category, effective.getNotificationFeedbackSettings())
			.ifPresent(playback ->
			{
				log.debug("{} haptic requested", category);
				sendPattern(
					hapticEventType(category),
					playback.getPatternSelection(),
					"ALERT_" + category.name(),
					playback.getIntensityPercent(),
					playback.getDurationMillis()
				);
			});
	}

	private void handleValuableLoot(Collection<ItemStack> items)
	{
		if (items == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long minimumValue = effectiveSettings().getAlertTriggerSettings()
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

	private static int classifyPoisonState(int poisonValue)
	{
		if (poisonValue <= 0)
		{
			return 0;
		}
		return poisonValue >= VENOM_THRESHOLD ? 2 : 1;
	}

	private void handleFeedbackTrigger(
		XpChange change,
		XpFeedbackTrigger trigger,
		RemoteSettingsSnapshot settings,
		XpFeedbackSettings skillXpSettings)
	{
		if (trigger == XpFeedbackTrigger.LEVEL_99)
		{
			if (isRemoteOutputPaused())
			{
				return;
			}
			log.debug(
				"Level 99 ceremony for {}: level {} -> {}",
				change.getSkill(),
				change.getPreviousLevel(),
				change.getCurrentLevel()
			);
			startLevel99Ceremony(change.getSkill(), true);
			return;
		}

		HapticPatternSelection preset;
		switch (trigger)
		{
			case XP_GAIN:
				preset = skillXpSettings.getPatternSelection();
				break;
			case LEVEL_UP:
				preset = settings.getLevelUpPatternPreset();
				break;
			case MILESTONE:
				preset = settings.getMilestonePatternPreset();
				break;
			case NONE:
			case LEVEL_99:
			default:
				return;
		}

		log.debug(
			"{} feedback for {}: {} XP, level {} -> {}",
			trigger,
			change.getSkill(),
			change.getGainedXp(),
			change.getPreviousLevel(),
			change.getCurrentLevel()
		);
		if (trigger == XpFeedbackTrigger.XP_GAIN)
		{
			sendPattern(
				HapticEventType.XP_GAIN,
				preset,
				trigger.name(),
				skillXpSettings.getIntensityPercent(),
				skillXpSettings.getDurationMillis()
			);
		}
		else
		{
			sendConfiguredPattern(
				trigger == XpFeedbackTrigger.MILESTONE
					? HapticEventType.MILESTONE
					: HapticEventType.LEVEL_UP,
				preset,
				trigger.name()
			);
		}
	}

	private void connectToIntiface()
	{
		String configuredServer = config.intifaceServer().trim();
		try
		{
			URI serverUri = new URI(configuredServer);
			String scheme = serverUri.getScheme();
			if (serverUri.getHost() == null
				|| !("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)))
			{
				throw new URISyntaxException(configuredServer, "Expected a ws:// or wss:// server URI");
			}
			intifaceService.connect(serverUri);
		}
		catch (URISyntaxException e)
		{
			panel.showInputError("Invalid Intiface server URI");
		}
	}

	private void sendTestPattern()
	{
		log.debug("Sending test haptic pattern");
		HapticScapePanel currentPanel = panel;
		if (currentPanel != null)
		{
			sendConfiguredPattern(
				HapticEventType.MANUAL_PREVIEW,
				currentPanel.getPatternPreset(),
				"TEST_XP"
			);
		}
	}

	private void sendTestLevelUpPattern()
	{
		log.debug("Sending test level-up pattern");
		HapticScapePanel currentPanel = panel;
		if (currentPanel != null)
		{
			sendConfiguredPattern(
				HapticEventType.MANUAL_PREVIEW,
				currentPanel.getLevelUpPatternPreset(),
				"TEST_LEVEL_UP"
			);
		}
	}

	private void previewLevel99Ceremony()
	{
		HapticScapePanel currentPanel = panel;
		Skill skill = currentPanel == null ? null : currentPanel.getSelectedProfileSkill();
		startLevel99Ceremony(skill == null ? Skill.ATTACK : skill, false);
	}

	private void playLevel99Cheer()
	{
		try
		{
			audioPlayer.play(
				HapticScapePlugin.class,
				LEVEL_99_CHEER_RESOURCE,
				LEVEL_99_CHEER_GAIN_DB
			);
		}
		catch (Exception e)
		{
			log.warn("Unable to play Level 99 cheer", e);
		}
	}

	private void startLevel99Ceremony(Skill skill, boolean announceInChat)
	{
		level99CelebrationController.start(skill);
		CompletableFuture.runAsync(this::playLevel99Cheer);
		if (announceInChat)
		{
			String coloredMessage = ColorUtil.wrapWithColorTag(
				Level99Ceremony.CHAT_MESSAGE,
				new Color(255, 174, 0)
			);
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(coloredMessage)
				.build());
		}
		if (intifaceService != null)
		{
			intifaceService.play(new HapticRequest(
				HapticEventType.LEVEL_99,
				Level99Ceremony.pattern()
			));
		}
	}

	private void sendTestSkillProfile()
	{
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}

		Skill skill = currentPanel.getSelectedProfileSkill();
		if (skill == null)
		{
			return;
		}

		XpFeedbackSettings settings = currentPanel.getXpFeedbackSettings(skill);
		log.debug("Sending test XP profile for {}", skill);
		sendPattern(
			HapticEventType.MANUAL_PREVIEW,
			settings.getPatternSelection(),
			"TEST_SKILL_" + skill.name(),
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void sendTestGenericNotificationPattern()
	{
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}

		NotificationFeedbackSettings settings =
			currentPanel.getNotificationFeedbackSettings();
		if (currentPanel.isGenericNotificationClickEnabled() && clickerService != null)
		{
			clickerService.click();
		}
		if (!settings.isEnabled())
		{
			return;
		}
		log.debug("Sending test generic notification pattern");
		sendPattern(
			HapticEventType.MANUAL_PREVIEW,
			settings.getPatternSelection(),
			"TEST_ALERT_GENERIC_NOTIFICATION",
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void sendTestAlert(AlertCategory category)
	{
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		if (currentPanel.isAlertClickEnabled(category) && clickerService != null)
		{
			clickerService.click();
		}

		currentPanel.getAlertProfiles()
			.resolve(category, currentPanel.getNotificationFeedbackSettings())
			.ifPresent(playback -> sendPattern(
				HapticEventType.MANUAL_PREVIEW,
				playback.getPatternSelection(),
				"TEST_ALERT_" + category.name(),
				playback.getIntensityPercent(),
				playback.getDurationMillis()
			));
	}

	private void sendPatternForgePreview(CustomPatternEntry pattern)
	{
		HapticScapePanel currentPanel = panel;
		if (intifaceService == null || currentPanel == null)
		{
			return;
		}

		log.debug(
			"Previewing unsaved Pattern Forge curve as {} beats of {} ms",
			pattern.getBeatCount(),
			pattern.getBeatDurationMillis()
		);
		intifaceService.play(new HapticRequest(
			HapticEventType.MANUAL_PREVIEW,
			pattern.createPattern(1.0)
		));
	}

	private void playRogueUnlockStingAsync()
	{
		CompletableFuture.runAsync(this::playRogueUnlockSting);
	}

	private void playRogueUnlockSting()
	{
		try
		{
			audioPlayer.play(
				HapticScapePlugin.class,
				ROGUE_UNLOCK_STING_RESOURCE,
				ROGUE_UNLOCK_STING_GAIN_DB
			);
		}
		catch (Exception e)
		{
			log.warn("Unable to play Rogue Mode unlock sting", e);
		}
	}

	private void dispatchRogueFeedback(RogueFeedbackEvent event)
	{
		if (event == null)
		{
			return;
		}

		if (event.shouldClick() && clickerService != null)
		{
			clickerService.click();
		}

		if (intifaceService == null)
		{
			return;
		}

		double scale = effectiveSettings().getGlobalXpFeedbackSettings()
			.getIntensityPercent() / 100.0;
		intifaceService.play(CasinoFeedbackMapper.toRequest(event, scale));
	}

	private void sendConfiguredPattern(
		HapticEventType eventType,
		HapticPatternSelection preset,
		String triggerName)
	{
		if (intifaceService == null)
		{
			return;
		}

		XpFeedbackSettings global = effectiveSettings().getGlobalXpFeedbackSettings();
		int intensityPercent = global.getIntensityPercent();
		int durationMillis = global.getDurationMillis();
		sendPattern(eventType, preset, triggerName, intensityPercent, durationMillis);
	}

	private void sendPattern(
		HapticEventType eventType,
		HapticPatternSelection preset,
		String triggerName,
		int intensityPercent,
		int durationMillis)
	{
		if (intifaceService == null)
		{
			return;
		}

		double intensity = intensityPercent / 100.0;
		Duration duration = Duration.ofMillis(durationMillis);
		HapticPattern pattern = preset.createPattern(
			effectiveSettings().getCustomPatterns(),
			intensity,
			duration
		);
		long playbackDurationMillis = pattern.getSteps().stream()
			.map(HapticPattern.Step::getDuration)
			.mapToLong(Duration::toMillis)
			.sum();
		log.debug(
			"Requesting {} pattern for {} at {}% for {} ms",
			preset,
			triggerName,
			intensityPercent,
			playbackDurationMillis
		);
		intifaceService.play(new HapticRequest(eventType, pattern));
	}

	private static HapticEventType hapticEventType(AlertCategory category)
	{
		switch (category)
		{
			case DIRECT_MESSAGE:
				return HapticEventType.DIRECT_MESSAGE;
			case TRADE_REQUEST:
				return HapticEventType.TRADE_REQUEST;
			case LOW_HITPOINTS:
				return HapticEventType.LOW_HITPOINTS;
			case LOW_PRAYER:
				return HapticEventType.LOW_PRAYER;
			case VALUABLE_DROP:
				return HapticEventType.VALUABLE_DROP;
			case INVENTORY_FULL:
				return HapticEventType.INVENTORY_FULL;
			case POISONED_OR_VENOMED:
				return HapticEventType.POISONED_OR_VENOMED;
			case SPECIAL_ATTACK_READY:
				return HapticEventType.SPECIAL_ATTACK_READY;
			case PLAYER_DEATH:
				return HapticEventType.PLAYER_DEATH;
			default:
				throw new IllegalArgumentException("Unknown alert category: " + category);
		}
	}

	private static BufferedImage loadNavigationIcon()
	{
		BufferedImage source = ImageUtil.loadImageResource(
			HapticScapePlugin.class,
			"/hapticscape.png"
		);
		BufferedImage scaled = ImageUtil.resizeImage(source, 16, 16, true);
		return ImageUtil.resizeCanvas(scaled, 16, 16);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private RemoteSettingsSnapshot effectiveSettings()
	{
		EffectiveSettingsService service = effectiveSettingsService;
		return service == null
			? RemoteSettingsSnapshot.capture(config)
			: service.current();
	}

	private boolean isRemoteOutputPaused()
	{
		RemoteSessionManager manager = remoteSessionManager;
		if (manager == null)
		{
			return false;
		}
		RemoteSessionSnapshot current = manager.getSnapshot();
		return current.getRole() == RemoteRole.PARTICIPANT
			&& current.getState() != RemoteSessionState.ACTIVE
			&& current.getState() != RemoteSessionState.LOCAL;
	}

	private void handleRemoteSessionChanged(RemoteSessionSnapshot snapshot)
	{
		boolean pauseOutput = snapshot.getRole() == RemoteRole.PARTICIPANT
			&& snapshot.getState() != RemoteSessionState.ACTIVE
			&& snapshot.getState() != RemoteSessionState.LOCAL;

		GatedIntifaceService hapticGate = intifaceService;
		if (hapticGate != null)
		{
			hapticGate.setOutputPaused(pauseOutput);
		}
		ClickerService clicks = clickerService;
		if (clicks != null)
		{
			clicks.setPaused(pauseOutput);
			if (!pauseOutput)
			{
				clicks.updateSettings(effectiveSettings().getClickerSettings());
			}
		}
		MusicSyncService music = musicSyncService;
		if (music != null)
		{
			if (pauseOutput)
			{
				music.stopNow();
			}
			else
			{
				music.updateSettings(effectiveSettings().getMusicSyncSettings());
			}
		}
	}

	private void handleRemoteSettingsChanged(RemoteSettingsSnapshot settings)
	{
		RemoteSessionManager manager = remoteSessionManager;
		if (manager == null || manager.getSnapshot().getRole() != RemoteRole.PARTICIPANT)
		{
			return;
		}
		ClickerService clicks = clickerService;
		if (clicks != null)
		{
			clicks.updateSettings(settings.getClickerSettings());
		}
		MusicSyncService music = musicSyncService;
		if (music != null && !isRemoteOutputPaused())
		{
			music.updateSettings(settings.getMusicSyncSettings());
		}
	}

	private MusicSyncSettings musicSettingsFromConfig()
	{
		MusicResponse response;
		try
		{
			response = MusicResponse.valueOf(config.musicResponse());
		}
		catch (IllegalArgumentException | NullPointerException ignored)
		{
			response = MusicResponse.RHYTHMIC;
		}
		int maximum = clamp(config.musicMaximumIntensityPercent(), 0, 100);
		int minimum = Math.min(
			clamp(config.musicMinimumIntensityPercent(), 0, 100),
			maximum
		);
		return new MusicSyncSettings(
			config.musicSyncEnabled(),
			response,
			clamp(config.musicSensitivityPercent(), 25, 200),
			minimum,
			maximum
		);
	}

	private ClickerSettings clickerSettingsFromConfig()
	{
		return new ClickerSettings(
			config.clickerEnabled(),
			clamp(
				config.clickerVolumePercent(),
				ClickerSettings.MINIMUM_VOLUME_PERCENT,
				ClickerSettings.MAXIMUM_VOLUME_PERCENT
			)
		);
	}

	@Provides
	HapticScapeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HapticScapeConfig.class);
	}
}
