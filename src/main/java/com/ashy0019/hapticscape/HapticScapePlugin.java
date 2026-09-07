package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticEventType;
import com.ashy0019.hapticscape.protocol.LocalhostGameplayEventTransport;
import com.ashy0019.hapticscape.protocol.LocalhostTransportEndpoint;
import com.ashy0019.hapticscape.protocol.TransportWireCodec;
import com.ashy0019.hapticscape.integration.desktop.DesktopAudioCaptureSources;
import com.ashy0019.hapticscape.integration.desktop.AwtExternalLinkOpener;
import com.ashy0019.hapticscape.integration.desktop.AwtGlobalUiHooks;
import com.ashy0019.hapticscape.integration.desktop.AwtTextClipboard;
import com.ashy0019.hapticscape.integration.desktop.DesktopDiscordDeepLinkInbox;
import com.ashy0019.hapticscape.integration.desktop.DesktopSecretProtectors;
import com.ashy0019.hapticscape.remote.DiscordJoinConsentHandler;
import com.ashy0019.hapticscape.remote.DiscordJoinRequest;
import com.ashy0019.hapticscape.remote.DiscordPairingBridge;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteDesktopNotificationService;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteGameplayBridge;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteHapticScapePluginPanel;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteLevel99CelebrationOverlay;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSkillCatalog;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSourceMessageService;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteStoragePaths;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSettingsWriter;
import com.ashy0019.hapticscape.integration.runelite.RuneLiteSoundPlayer;
import com.ashy0019.hapticscape.remote.SettingsStore;
import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import com.ashy0019.hapticscape.ui.HapticScapePanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
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
	private HapticScapeRuntime runtime;
	private RuneLiteGameplayBridge gameplayBridge;
	private LocalhostGameplayEventTransport gameplayTransport;
	private HapticScapePanel panel;
	private RuneLiteHapticScapePluginPanel panelHost;
	private NavigationButton navigationButton;
	private RuneLiteLevel99CelebrationOverlay level99CelebrationOverlay;

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
		SkillCatalog skillCatalog = RuneLiteSkillCatalog.getNeutralCatalog();
		HapticScapeStoragePaths storagePaths = RuneLiteStoragePaths.create();
		SettingsStore settingsStore =
			new RuneLiteSettingsWriter(configManager, HapticScapeConfig.GROUP);
		runtime = new HapticScapeRuntime(new HapticScapeRuntimeDependencies(
			httpClient,
			gson,
			config,
			settingsStore,
			skillCatalog,
			storagePaths,
			new RuneLiteSoundPlayer(audioPlayer),
			new RuneLiteDesktopNotificationService(notifier, runeLiteConfig),
			new RuneLiteSourceMessageService(chatMessageManager),
			DesktopAudioCaptureSources::systemOutput,
			DesktopSecretProtectors.savedUnlockKeys(),
			DesktopSecretProtectors.discordCredentials(),
			LocalhostTransportEndpoint.DEFAULT_PORT
		));
		runtime.start();

		level99CelebrationOverlay = new RuneLiteLevel99CelebrationOverlay(
			this,
			runtime.getLevel99CelebrationController()
		);
		overlayManager.add(level99CelebrationOverlay);

		TransportWireCodec gameplayTransportCodec = new TransportWireCodec(gson);
		gameplayTransport = new LocalhostGameplayEventTransport(
			"runelite",
			gameplayTransportCodec,
			runtime.getGameplayTransportPort()
		);
		gameplayBridge = new RuneLiteGameplayBridge(client, itemManager, gameplayTransport);
		gameplayBridge.start();

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
			runtime.getIntifaceService()::disconnect,
			this::sendTestPattern,
			this::sendTestLevelUpPattern,
			this::previewLevel99Ceremony,
			this::sendTestSkillProfile,
			this::sendTestGenericNotificationPattern,
			this::sendTestAlert,
			runtime::previewCustomPattern,
			runtime.getMusicSyncService()::updateSettings,
			runtime.getClickerService()::updateSettings,
			runtime::playClick,
			runtime.getUpdatePreferencesStore(),
			runtime.getUpdateCheckService(),
			runtime.getRemoteSessionManager(),
			runtime.getRemotePairingService(),
			runtime.getDiscordPairingBridge(),
			runtime.getSettingsLockService(),
			runtime::dispatchRogueFeedback,
			runtime::playRogueUnlockStingAsync,
			runtime::stopAll
		);
		panelHost.setContent(panel);
		runtime.getIntifaceService().setConnectionListener(panel::updateConnection);
		runtime.getMusicSyncService().setListener(panel::updateMusicSync);

		navigationButton = NavigationButton.builder()
			.tooltip("HapticScape")
			.icon(loadNavigationIcon())
			.panel(panelHost)
			.priority(5)
			.build();
		clientToolbar.addNavigation(navigationButton);

		DiscordPairingBridge discordPairingBridge = runtime.getDiscordPairingBridge();
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
		if (gameplayTransport != null)
		{
			gameplayTransport.close();
			gameplayTransport = null;
		}

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
		if (panel != null)
		{
			panel.close();
			panel = null;
		}
		panelHost = null;
		if (runtime != null)
		{
			runtime.close();
			runtime = null;
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
			HapticScapeRuntime currentRuntime = runtime;
			if (currentRuntime != null)
			{
				currentRuntime.getLevel99CelebrationController().reset();
			}
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
		HapticScapeRuntime currentRuntime = runtime;
		if (currentRuntime == null)
		{
			return;
		}
		try
		{
			currentRuntime.connectToIntiface();
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
		HapticScapeRuntime currentRuntime = runtime;
		if (currentPanel != null && currentRuntime != null)
		{
			currentRuntime.sendConfiguredPattern(
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
		HapticScapeRuntime currentRuntime = runtime;
		if (currentPanel != null && currentRuntime != null)
		{
			currentRuntime.sendConfiguredPattern(
				HapticEventType.MANUAL_PREVIEW,
				currentPanel.getLevelUpPatternPreset(),
				"TEST_LEVEL_UP"
			);
		}
	}

	private void previewLevel99Ceremony()
	{
		HapticScapePanel currentPanel = panel;
		HapticScapeRuntime currentRuntime = runtime;
		if (currentRuntime == null)
		{
			return;
		}
		String skillId = currentPanel == null ? null : currentPanel.getSelectedProfileSkillId();
		currentRuntime.startLevel99Ceremony(
			skillId == null ? "attack" : skillId,
			false
		);
	}


	private void sendTestSkillProfile()
	{
		HapticScapePanel currentPanel = panel;
		HapticScapeRuntime currentRuntime = runtime;
		if (currentPanel == null || currentRuntime == null)
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
		currentRuntime.sendPattern(
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
		HapticScapeRuntime currentRuntime = runtime;
		if (currentPanel == null || currentRuntime == null)
		{
			return;
		}

		NotificationFeedbackSettings settings =
			currentPanel.getNotificationFeedbackSettings();
		if (currentPanel.isGenericNotificationClickEnabled())
		{
			currentRuntime.playClick();
		}
		if (!settings.isEnabled())
		{
			return;
		}
		log.debug("Sending test generic notification pattern");
		currentRuntime.sendPattern(
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
		HapticScapeRuntime currentRuntime = runtime;
		if (currentPanel == null || currentRuntime == null)
		{
			return;
		}
		if (currentPanel.isAlertClickEnabled(category))
		{
			currentRuntime.playClick();
		}

		currentPanel.getAlertProfiles()
			.resolve(category, currentPanel.getNotificationFeedbackSettings())
			.ifPresent(playback -> currentRuntime.sendPattern(
				HapticEventType.MANUAL_PREVIEW,
				playback.getPatternSelection(),
				"TEST_ALERT_" + category.name(),
				playback.getIntensityPercent(),
				playback.getDurationMillis()
			));
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


	@Provides
	HapticScapeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HapticScapeConfig.class);
	}
}
