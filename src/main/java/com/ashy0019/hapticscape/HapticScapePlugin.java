package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.DefaultIntifaceService;
import com.ashy0019.hapticscape.device.HapticPattern;
import com.ashy0019.hapticscape.device.IntifaceService;
import com.ashy0019.hapticscape.ui.HapticScapePanel;
import com.ashy0019.hapticscape.ui.Level99CelebrationOverlay;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
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
	private static final String LEVEL_99_CHEER_RESOURCE = "/level99-cheer.wav";
	private static final float LEVEL_99_CHEER_GAIN_DB = -4.0f;

	private final XpTracker xpTracker = new XpTracker();
	private final ThresholdAlertTracker thresholdAlertTracker = new ThresholdAlertTracker();
	private final AlertDeduplicator alertDeduplicator = new AlertDeduplicator();
	private final Level99CelebrationController level99CelebrationController =
		new Level99CelebrationController();
	private IntifaceService intifaceService;
	private HapticScapePanel panel;
	private NavigationButton navigationButton;
	private Level99CelebrationOverlay level99CelebrationOverlay;
	private ScheduledExecutorService alertScheduler;

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
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Override
	protected void startUp()
	{
		xpTracker.reset();
		thresholdAlertTracker.reset();
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

		intifaceService = new DefaultIntifaceService(httpClient, gson);
		panel = new HapticScapePanel(
			config,
			configManager,
			this::connectToIntiface,
			intifaceService::disconnect,
			this::sendTestPattern,
			this::sendTestLevelUpPattern,
			this::previewLevel99Ceremony,
			this::sendTestSkillProfile,
			this::sendTestAlert,
			this::sendPatternForgePreview,
			intifaceService::stopAll
		);
		intifaceService.setConnectionListener(panel::updateConnection);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			seedThresholdAlerts();
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
		thresholdAlertTracker.reset();
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
			seedThresholdAlerts();
		}
		else if (gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.HOPPING
			|| gameState == GameState.CONNECTION_LOST)
		{
			xpTracker.reset();
			thresholdAlertTracker.reset();
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
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null || !currentPanel.isSkillEnabled(change.getSkill()))
		{
			return;
		}

		XpFeedbackSettings skillXpSettings = currentPanel.getXpFeedbackSettings(change.getSkill());
		XpFeedbackTrigger trigger = XpFeedbackTrigger.classify(
			change,
			skillXpSettings.getMinimumXpGain(),
			currentPanel.isLevelUpFeedbackEnabled(),
			currentPanel.isMilestoneFeedbackEnabled(),
			currentPanel.isLevel99CelebrationEnabled()
		);
		handleFeedbackTrigger(change, trigger, currentPanel, skillXpSettings);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		switch (event.getType())
		{
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				dispatchSpecificAlert(AlertCategory.DIRECT_MESSAGE);
				break;
			case TRADEREQ:
				if (event.getMessage().contains("wishes to trade with you."))
				{
					dispatchSpecificAlert(AlertCategory.TRADE_REQUEST);
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}

		NotificationFeedbackSettings settings =
			currentPanel.getNotificationFeedbackSettings();
		if (!settings.shouldPlay(
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
						dispatchAlert(AlertCategory.GENERIC_NOTIFICATION);
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

	private void seedThresholdAlerts()
	{
		thresholdAlertTracker.seed(
			AlertCategory.LOW_HITPOINTS,
			client.getBoostedSkillLevel(Skill.HITPOINTS)
		);
		thresholdAlertTracker.seed(
			AlertCategory.LOW_PRAYER,
			client.getBoostedSkillLevel(Skill.PRAYER)
		);
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

		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		int threshold = currentPanel.getAlertProfiles().get(category).getThreshold();
		if (thresholdAlertTracker.update(category, event.getBoostedLevel(), threshold))
		{
			dispatchSpecificAlert(category);
		}
	}

	private void dispatchSpecificAlert(AlertCategory category)
	{
		alertDeduplicator.recordSpecificAlert(System.nanoTime());
		dispatchAlert(category);
	}

	private void dispatchAlert(AlertCategory category)
	{
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		currentPanel.getAlertProfiles()
			.resolve(category, currentPanel.getNotificationFeedbackSettings())
			.ifPresent(playback ->
			{
				log.debug("{} haptic requested", category);
				sendPattern(
					playback.getPatternSelection(),
					"ALERT_" + category.name(),
					playback.getIntensityPercent(),
					playback.getDurationMillis()
				);
			});
	}

	private void handleFeedbackTrigger(
		XpChange change,
		XpFeedbackTrigger trigger,
		HapticScapePanel currentPanel,
		XpFeedbackSettings skillXpSettings)
	{
		if (trigger == XpFeedbackTrigger.LEVEL_99)
		{
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
				preset = currentPanel.getLevelUpPatternPreset();
				break;
			case MILESTONE:
				preset = currentPanel.getMilestonePatternPreset();
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
				preset,
				trigger.name(),
				skillXpSettings.getIntensityPercent(),
				skillXpSettings.getDurationMillis()
			);
		}
		else
		{
			sendConfiguredPattern(preset, trigger.name());
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
			sendConfiguredPattern(currentPanel.getPatternPreset(), "TEST_XP");
		}
	}

	private void sendTestLevelUpPattern()
	{
		log.debug("Sending test level-up pattern");
		HapticScapePanel currentPanel = panel;
		if (currentPanel != null)
		{
			sendConfiguredPattern(currentPanel.getLevelUpPatternPreset(), "TEST_LEVEL_UP");
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
			intifaceService.playPattern(Level99Ceremony.pattern());
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
			settings.getPatternSelection(),
			"TEST_SKILL_" + skill.name(),
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

		NotificationFeedbackSettings configured =
			currentPanel.getNotificationFeedbackSettings();
		NotificationFeedbackSettings previewSettings = new NotificationFeedbackSettings(
			true,
			configured.isRespectRuneLiteFocus(),
			configured.getIntensityPercent(),
			configured.getDurationMillis(),
			configured.getPatternSelection()
		);
		currentPanel.getAlertProfiles()
			.resolve(category, previewSettings)
			.ifPresent(playback -> sendPattern(
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
		intifaceService.playPattern(pattern.createPattern(1.0));
	}

	private void sendConfiguredPattern(HapticPatternSelection preset, String triggerName)
	{
		HapticScapePanel currentPanel = panel;
		if (intifaceService == null || currentPanel == null)
		{
			return;
		}

		int intensityPercent = currentPanel.getIntensityPercent();
		int durationMillis = currentPanel.getPulseDurationMillis();
		sendPattern(preset, triggerName, intensityPercent, durationMillis);
	}

	private void sendPattern(
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
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		HapticPattern pattern = preset.createPattern(
			currentPanel.getCustomPatterns(),
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
		intifaceService.playPattern(pattern);
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
