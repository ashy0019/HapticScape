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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
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
		panel = null;

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

		int gainedXp = xpTracker.update(event.getSkill(), event.getXp());
		HapticScapePanel currentPanel = panel;
		if (currentPanel != null
			&& currentPanel.isSkillEnabled(event.getSkill())
			&& gainedXp >= currentPanel.getMinimumXpGain())
		{
			onQualifiedXpGain(event.getSkill(), gainedXp);
		}
	}

	private void seedCurrentXp()
	{
		for (Skill skill : Skill.values())
		{
			xpTracker.seed(skill, client.getSkillExperience(skill));
		}
	}

	private void onQualifiedXpGain(Skill skill, int gainedXp)
	{
		log.debug("Qualified XP gain: {} XP in {}", gainedXp, skill);
		sendConfiguredPattern();
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
		sendConfiguredPattern();
	}

	private void sendConfiguredPattern()
	{
		HapticScapePanel currentPanel = panel;
		if (intifaceService == null || currentPanel == null)
		{
			return;
		}

		int intensityPercent = currentPanel.getIntensityPercent();
		int durationMillis = currentPanel.getPulseDurationMillis();
		HapticPatternPreset preset = currentPanel.getPatternPreset();
		log.debug(
			"Requesting {} pattern at {}% for {} ms",
			preset,
			intensityPercent,
			durationMillis
		);

		double intensity = intensityPercent / 100.0;
		Duration duration = Duration.ofMillis(durationMillis);
		HapticPattern pattern = preset.createPattern(intensity, duration);
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
