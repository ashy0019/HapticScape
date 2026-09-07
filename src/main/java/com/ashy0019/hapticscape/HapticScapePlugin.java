package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.audio.HapticScapeSound;
import com.ashy0019.hapticscape.audio.SoundPlayer;
import com.ashy0019.hapticscape.clicker.SoundPlayerClickPlayback;
import com.ashy0019.hapticscape.clicker.ClickerService;
import com.ashy0019.hapticscape.clicker.ClickerSettings;
import com.ashy0019.hapticscape.device.DefaultIntifaceService;
import com.ashy0019.hapticscape.device.GatedIntifaceService;
import com.ashy0019.hapticscape.device.HapticEventType;
import com.ashy0019.hapticscape.device.HapticRequest;
import com.ashy0019.hapticscape.music.MusicResponse;
import com.ashy0019.hapticscape.music.MusicSyncService;
import com.ashy0019.hapticscape.music.MusicSyncSettings;
import com.ashy0019.hapticscape.integration.desktop.DesktopAudioCaptureSources;
import com.ashy0019.hapticscape.remote.DiscordCredentialStore;
import com.ashy0019.hapticscape.integration.desktop.AwtExternalLinkOpener;
import com.ashy0019.hapticscape.integration.desktop.AwtGlobalUiHooks;
import com.ashy0019.hapticscape.integration.desktop.AwtTextClipboard;
import com.ashy0019.hapticscape.integration.desktop.DesktopDiscordDeepLinkInbox;
import com.ashy0019.hapticscape.integration.desktop.DesktopSecretProtectors;
import com.ashy0019.hapticscape.remote.DiscordJoinConsentHandler;
import com.ashy0019.hapticscape.remote.DiscordJoinRequest;
import com.ashy0019.hapticscape.remote.DiscordPairingBridge;
import com.ashy0019.hapticscape.remote.EffectiveSettingsService;
import com.ashy0019.hapticscape.remote.RemotePairingService;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionManager;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.host.DesktopNotificationService;
import com.ashy0019.hapticscape.host.SourceMessageService;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteDesktopNotificationService;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteGameplayBridge;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteHapticScapePluginPanel;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteLevel99CelebrationOverlay;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSkillCatalog;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSourceMessageService;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteStoragePaths;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSettingsWriter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSoundPlayer;
import com.ashy0019.hapticscape.remote.RemoteSettingsSnapshot;
import com.ashy0019.hapticscape.remote.SavedUnlockKeyStore;
import com.ashy0019.hapticscape.remote.SettingsBackedRemotePermissionsStore;
import com.ashy0019.hapticscape.remote.SettingsBackedRemoteSettingsStore;
import com.ashy0019.hapticscape.remote.SettingsStore;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.remote.SettingsLockService;
import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import com.ashy0019.hapticscape.ui.HapticScapePanel;
import com.ashy0019.hapticscape.update.UpdateCheckService;
import com.ashy0019.hapticscape.update.UpdatePreferencesStore;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "HapticScape",
	description = "Triggers configurable Intiface device feedback from game events",
	tags = {"intiface", "haptics", "feedback", "xp", "accessibility"}
)
public class HapticScapePlugin extends Plugin
{
	private static final float LEVEL_99_CHEER_GAIN_DB = -4.0f;
	private static final float ROGUE_UNLOCK_STING_GAIN_DB = -4.0f;
	private final Level99CelebrationController level99CelebrationController =
		new Level99CelebrationController();
	private GameplayEventCoordinator gameplayEvents;
	private RuneLiteGameplayBridge gameplayBridge;
	private FeedbackCoordinator feedbackCoordinator;
	private GatedIntifaceService intifaceService;
	private EffectiveSettingsService effectiveSettingsService;
	private RemoteSessionManager remoteSessionManager;
	private DiscordPairingBridge discordPairingBridge;
	private SettingsLockService settingsLockService;
	private MusicSyncService musicSyncService;
	private ClickerService clickerService;
	private SoundPlayer soundPlayer;
	private SourceMessageService sourceMessages;
	private HapticScapePanel panel;
	private RuneLiteHapticScapePluginPanel panelHost;
	private NavigationButton navigationButton;
	private RuneLiteLevel99CelebrationOverlay level99CelebrationOverlay;
	private UpdatePreferencesStore updatePreferencesStore;
	private UpdateCheckService updateCheckService;

	@Inject
	private Client client;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private HapticScapeConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RuneLiteConfig runeLiteConfig;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientUI clientUI;

	@Inject
	private Notifier notifier;

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
		level99CelebrationController.reset();
		level99CelebrationOverlay = new RuneLiteLevel99CelebrationOverlay(
			this,
			level99CelebrationController
		);
		overlayManager.add(level99CelebrationOverlay);

		intifaceService = new GatedIntifaceService(
			new DefaultIntifaceService(httpClient, gson)
		);
		effectiveSettingsService = new EffectiveSettingsService(config);
		SkillCatalog skillCatalog = RuneLiteSkillCatalog.getNeutralCatalog();
		SettingsLockCatalog.registerSkills(skillCatalog.getSkills());
		HapticScapeStoragePaths storagePaths = RuneLiteStoragePaths.create();
		settingsLockService = new SettingsLockService(gson, storagePaths);
		musicSyncService = new MusicSyncService(
			intifaceService,
			DesktopAudioCaptureSources::systemOutput,
			musicSettingsFromConfig()
		);
		soundPlayer = new RuneLiteSoundPlayer(audioPlayer);
		clickerService = new ClickerService(
			new SoundPlayerClickPlayback(soundPlayer),
			clickerSettingsFromConfig()
		);
		DesktopNotificationService desktopNotifications =
			new RuneLiteDesktopNotificationService(notifier, runeLiteConfig);
		sourceMessages = new RuneLiteSourceMessageService(chatMessageManager);
		feedbackCoordinator = new FeedbackCoordinator(
			intifaceService,
			clickerService,
			musicSyncService,
			this::effectiveSettings,
			this::startLevel99CeremonyBySkillId,
			desktopNotifications,
			sourceMessages
		);
		SettingsStore settingsStore =
			new RuneLiteSettingsWriter(configManager, HapticScapeConfig.GROUP);
		SavedUnlockKeyStore savedUnlockKeyStore = new SavedUnlockKeyStore(
			gson,
			storagePaths,
			DesktopSecretProtectors.savedUnlockKeys()
		);
		remoteSessionManager = new RemoteSessionManager(
			httpClient,
			gson,
			new SettingsBackedRemoteSettingsStore(config, settingsStore),
			effectiveSettingsService,
			settingsLockService,
			savedUnlockKeyStore,
			new SettingsBackedRemotePermissionsStore(config, settingsStore),
			feedbackCoordinator.createRemoteActionExecutor()
		);
		remoteSessionManager.addListener(new RemoteSessionListener()
		{
			@Override
			public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
			{
				feedbackCoordinator.handleRemoteSessionChanged(snapshot);
			}

			@Override
			public void onRemoteSettingsChanged(RemoteSettingsSnapshot settings)
			{
				feedbackCoordinator.handleRemoteSettingsChanged(
					settings,
					remoteSessionManager.getSnapshot()
				);
			}
		});
		gameplayEvents = new GameplayEventCoordinator(
			this::effectiveSettings,
			feedbackCoordinator
		);
		gameplayEvents.start();
		gameplayBridge = new RuneLiteGameplayBridge(client, itemManager, gameplayEvents);
		gameplayBridge.start();
		updatePreferencesStore = new UpdatePreferencesStore(gson, storagePaths);
		updateCheckService = new UpdateCheckService(httpClient, gson);
		RemotePairingService remotePairingService = new RemotePairingService(httpClient);
		discordPairingBridge = new DiscordPairingBridge(
			httpClient,
			gson,
			remoteSessionManager,
			remotePairingService,
			new DiscordCredentialStore(
				gson,
				storagePaths,
				DesktopSecretProtectors.discordCredentials()
			)
		);
		discordPairingBridge.start();
		panelHost = new RuneLiteHapticScapePluginPanel();
		panel = new HapticScapePanel(
			config,
			skillCatalog,
			settingsStore,
			new AwtExternalLinkOpener(),
			new AwtTextClipboard(),
			new AwtGlobalUiHooks(),
			panelHost.getSidebarScrollPane(),
			this::connectToIntiface,
			intifaceService::disconnect,
			this::sendTestPattern,
			this::sendTestLevelUpPattern,
			this::previewLevel99Ceremony,
			this::sendTestSkillProfile,
			this::sendTestGenericNotificationPattern,
			this::sendTestAlert,
			feedbackCoordinator::previewCustomPattern,
			musicSyncService::updateSettings,
			clickerService::updateSettings,
			clickerService::click,
			updatePreferencesStore,
			updateCheckService,
			remoteSessionManager,
			remotePairingService,
			discordPairingBridge,
			settingsLockService,
			feedbackCoordinator::dispatchRogueFeedback,
			this::playRogueUnlockStingAsync,
			feedbackCoordinator::stopAll
		);
		panelHost.setContent(panel);
		intifaceService.setConnectionListener(panel::updateConnection);
		musicSyncService.setListener(panel::updateMusicSync);
		musicSyncService.updateSettings(effectiveSettingsService.current().getMusicSyncSettings());
		navigationButton = NavigationButton.builder()
			.tooltip("HapticScape")
			.icon(loadNavigationIcon())
			.panel(panelHost)
			.priority(5)
			.build();
		clientToolbar.addNavigation(navigationButton);
		discordPairingBridge.setJoinConsentHandler(new DiscordJoinConsentHandler()
		{
			@Override
			public void onDeepLinkOpened()
			{
				SwingUtilities.invokeLater(HapticScapePlugin.this::focusRemotePlay);
			}

			@Override
			public CompletableFuture<Boolean> requestConsent(DiscordJoinRequest request)
			{
				CompletableFuture<Boolean> result = new CompletableFuture<>();
				SwingUtilities.invokeLater(() ->
				{
					try
					{
						focusRemotePlay();
						result.complete(panel.confirmDiscordRemoteControl(request));
					}
					catch (RuntimeException exception)
					{
						result.completeExceptionally(exception);
					}
				});
				return result;
			}

			@Override
			public void showError(String message)
			{
				SwingUtilities.invokeLater(() ->
				{
					focusRemotePlay();
					panel.showDiscordPairingError(message);
				});
			}
		});
		DesktopDiscordDeepLinkInbox.getInstance().setHandler(discordPairingBridge::acceptDeepLink);

		log.info("HapticScape started");
	}

	@Override
	protected void shutDown()
	{
		DesktopDiscordDeepLinkInbox.getInstance().setHandler(null);
		gameplayBridge = null;
		if (gameplayEvents != null)
		{
			gameplayEvents.close();
			gameplayEvents = null;
		}
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

		if (discordPairingBridge != null)
		{
			discordPairingBridge.setJoinConsentHandler(null);
			discordPairingBridge.close();
			discordPairingBridge = null;
		}
		if (remoteSessionManager != null)
		{
			remoteSessionManager.close();
			remoteSessionManager = null;
		}
		feedbackCoordinator = null;
		sourceMessages = null;
		if (musicSyncService != null)
		{
			musicSyncService.setListener(snapshot -> { });
			musicSyncService.close();
			musicSyncService = null;
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
		panelHost = null;
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
		log.info("HapticScape stopped");
	}

	private void focusRemotePlay()
	{
		clientUI.forceFocus();
		if (navigationButton != null)
		{
			clientToolbar.openPanel(navigationButton);
		}
		if (panel != null)
		{
			panel.showDiscordRemoteView();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onGameStateChanged(event.getGameState());
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.CONNECTION_LOST)
		{
			level99CelebrationController.reset();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onStatChanged(event);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onChatMessage(event);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onItemContainerChanged(event);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onVarbitChanged(event);
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onLootReceived(event.getItems());
		}
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onLootReceived(event.getItems());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onActorDeath(event);
		}
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		RuneLiteGameplayBridge bridge = gameplayBridge;
		if (bridge != null)
		{
			bridge.onNotificationFired(event, clientUI.isFocused());
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
		FeedbackCoordinator feedback = feedbackCoordinator;
		if (currentPanel != null && feedback != null)
		{
			feedback.sendConfiguredPattern(
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
		FeedbackCoordinator feedback = feedbackCoordinator;
		if (currentPanel != null && feedback != null)
		{
			feedback.sendConfiguredPattern(
				HapticEventType.MANUAL_PREVIEW,
				currentPanel.getLevelUpPatternPreset(),
				"TEST_LEVEL_UP"
			);
		}
	}

	private void previewLevel99Ceremony()
	{
		HapticScapePanel currentPanel = panel;
		String skillId = currentPanel == null ? null : currentPanel.getSelectedProfileSkillId();
		startLevel99CeremonyBySkillId(
			skillId == null ? "attack" : skillId,
			false
		);
	}

	private void playLevel99Cheer()
	{
		try
		{
			soundPlayer.play(
				HapticScapeSound.LEVEL_99_CHEER,
				LEVEL_99_CHEER_GAIN_DB
			);
		}
		catch (Exception e)
		{
			log.warn("Unable to play Level 99 cheer", e);
		}
	}

	private void startLevel99CeremonyBySkillId(String skillId, boolean announceInChat)
	{
		level99CelebrationController.start(
			RuneLiteSkillCatalog.getNeutralCatalog().require(skillId)
		);
		CompletableFuture.runAsync(this::playLevel99Cheer);
		if (announceInChat && sourceMessages != null)
		{
			sourceMessages.postColored(Level99Ceremony.CHAT_MESSAGE, 0xFFAE00);
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
		FeedbackCoordinator feedback = feedbackCoordinator;
		if (currentPanel == null || feedback == null)
		{
			return;
		}

		String skillId = currentPanel.getSelectedProfileSkillId();
		if (skillId == null)
		{
			return;
		}

		XpFeedbackSettings settings = currentPanel.getXpFeedbackSettings(skillId);
		log.debug("Sending test XP profile for {}", skillId);
		feedback.sendPattern(
			HapticEventType.MANUAL_PREVIEW,
			settings.getPatternSelection(),
			"TEST_SKILL_" + SkillIds.toConfigToken(skillId),
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void sendTestGenericNotificationPattern()
	{
		HapticScapePanel currentPanel = panel;
		FeedbackCoordinator feedback = feedbackCoordinator;
		if (currentPanel == null || feedback == null)
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
		feedback.sendPattern(
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
		FeedbackCoordinator feedback = feedbackCoordinator;
		if (currentPanel == null || feedback == null)
		{
			return;
		}
		if (currentPanel.isAlertClickEnabled(category) && clickerService != null)
		{
			clickerService.click();
		}

		currentPanel.getAlertProfiles()
			.resolve(category, currentPanel.getNotificationFeedbackSettings())
			.ifPresent(playback -> feedback.sendPattern(
				HapticEventType.MANUAL_PREVIEW,
				playback.getPatternSelection(),
				"TEST_ALERT_" + category.name(),
				playback.getIntensityPercent(),
				playback.getDurationMillis()
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
			soundPlayer.play(
				HapticScapeSound.ROGUE_UNLOCK_STING,
				ROGUE_UNLOCK_STING_GAIN_DB
			);
		}
		catch (Exception e)
		{
			log.warn("Unable to play Rogue Mode unlock sting", e);
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
