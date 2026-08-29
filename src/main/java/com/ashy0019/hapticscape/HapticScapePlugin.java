package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.DefaultIntifaceService;
import com.ashy0019.hapticscape.device.HapticPattern;
import com.ashy0019.hapticscape.device.IntifaceService;
import com.ashy0019.hapticscape.ui.HapticScapePanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
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
	private final XpTracker xpTracker = new XpTracker();
	private IntifaceService intifaceService;
	private HapticScapePanel panel;
	private NavigationButton navigationButton;

	@Inject
	private Client client;

	@Inject
	private HapticScapeConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientUI clientUI;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Override
	protected void startUp()
	{
		xpTracker.reset();

		intifaceService = new DefaultIntifaceService(httpClient, gson);
		panel = new HapticScapePanel(
			config,
			configManager,
			this::connectToIntiface,
			intifaceService::disconnect,
			this::sendTestPattern,
			this::sendTestLevelUpPattern,
			this::sendTestSkillProfile,
			this::sendTestNotificationPattern,
			this::sendPatternForgePreview,
			intifaceService::stopAll
		);
		intifaceService.setConnectionListener(panel::updateConnection);

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

		log.info("HapticScape stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();

		if (gameState == GameState.LOGGED_IN)
		{
			seedCurrentXp();
		}
		else if (gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.HOPPING
			|| gameState == GameState.CONNECTION_LOST)
		{
			xpTracker.reset();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

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
			currentPanel.isMilestoneFeedbackEnabled()
		);
		handleFeedbackTrigger(change, trigger, currentPanel, skillXpSettings);
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

		log.debug("RuneLite notification haptic requested");
		sendPattern(
			settings.getPatternSelection(),
			"RUNELITE_NOTIFICATION",
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void seedCurrentXp()
	{
		for (Skill skill : Skill.values())
		{
			xpTracker.seed(skill, client.getSkillExperience(skill));
		}
	}

	private void handleFeedbackTrigger(
		XpChange change,
		XpFeedbackTrigger trigger,
		HapticScapePanel currentPanel,
		XpFeedbackSettings skillXpSettings)
	{
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
			case LEVEL_99:
				preset = HapticPatternSelection.ASCENDING;
				break;
			case NONE:
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

	private void sendTestNotificationPattern()
	{
		HapticScapePanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}

		NotificationFeedbackSettings settings =
			currentPanel.getNotificationFeedbackSettings();
		log.debug("Sending test notification haptic pattern");
		sendPattern(
			settings.getPatternSelection(),
			"TEST_NOTIFICATION",
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void sendPatternForgePreview(CustomPatternEntry pattern)
	{
		HapticScapePanel currentPanel = panel;
		if (intifaceService == null || currentPanel == null)
		{
			return;
		}

		double intensity = currentPanel.getIntensityPercent() / 100.0;
		log.debug(
			"Previewing unsaved Pattern Forge curve as {} beats of {} ms",
			pattern.getBeatCount(),
			pattern.getBeatDurationMillis()
		);
		intifaceService.playPattern(pattern.createPattern(intensity));
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
