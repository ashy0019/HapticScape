package com.ashy0019.hapticscape.desktop;

import com.ashy0019.hapticscape.AlertCategory;
import com.ashy0019.hapticscape.HapticScapeRuntime;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.NotificationFeedbackSettings;
import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.SkillIds;
import com.ashy0019.hapticscape.XpFeedbackSettings;
import com.ashy0019.hapticscape.device.HapticEventType;
import com.ashy0019.hapticscape.integration.desktop.AwtExternalLinkOpener;
import com.ashy0019.hapticscape.integration.desktop.AwtGlobalUiHooks;
import com.ashy0019.hapticscape.integration.desktop.AwtTextClipboard;
import com.ashy0019.hapticscape.integration.desktop.DesktopSourceMessageService;
import com.ashy0019.hapticscape.integration.desktop.StandaloneLevel99GlassPane;
import com.ashy0019.hapticscape.remote.DiscordJoinConsentHandler;
import com.ashy0019.hapticscape.remote.DiscordJoinRequest;
import com.ashy0019.hapticscape.remote.DiscordPairingBridge;
import com.ashy0019.hapticscape.remote.SettingsStore;
import com.ashy0019.hapticscape.ui.HapticScapePanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Hosts the reusable HapticScape Swing panel in a standalone desktop window. */
public final class HapticScapeDesktopWindow implements AutoCloseable
{
	private final HapticScapeRuntime runtime;
	private final JFrame frame = new JFrame("HapticScape");
	private final JScrollPane pageScrollPane = new JScrollPane();
	private final JLabel sourceMessageLabel = new JLabel(" ");
	private final HapticScapePanel panel;
	private final DesktopSourceMessageService sourceMessages;
	private final Runnable closeAction;

	public HapticScapeDesktopWindow(
		HapticScapeRuntime runtime,
		HapticScapeSettingsSource settings,
		SkillCatalog skillCatalog,
		SettingsStore settingsStore,
		DesktopSourceMessageService sourceMessages,
		Runnable closeAction)
	{
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.sourceMessages = Objects.requireNonNull(sourceMessages, "sourceMessages");
		this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
		Objects.requireNonNull(settings, "settings");
		Objects.requireNonNull(skillCatalog, "skillCatalog");
		Objects.requireNonNull(settingsStore, "settingsStore");

		panel = new HapticScapePanel(
			settings,
			skillCatalog,
			settingsStore,
			new AwtExternalLinkOpener(),
			new AwtTextClipboard(),
			new AwtGlobalUiHooks(),
			pageScrollPane,
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
		pageScrollPane.setBorder(BorderFactory.createEmptyBorder());
		pageScrollPane.setViewportView(panel);
		pageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		pageScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		sourceMessageLabel.setBorder(BorderFactory.createEmptyBorder(5, 8, 6, 8));
		sourceMessageLabel.setOpaque(true);
		sourceMessageLabel.setBackground(new Color(25, 25, 25));
		sourceMessageLabel.setForeground(new Color(220, 220, 220));

		JPanel content = new JPanel(new BorderLayout());
		content.add(pageScrollPane, BorderLayout.CENTER);
		content.add(sourceMessageLabel, BorderLayout.SOUTH);
		frame.setContentPane(content);
		frame.setGlassPane(new StandaloneLevel99GlassPane(runtime.getLevel99CelebrationController()));
		frame.getGlassPane().setVisible(true);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setMinimumSize(new Dimension(390, 600));
		frame.setSize(500, 900);
		frame.setLocationByPlatform(true);
		frame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent event)
			{
				HapticScapeDesktopWindow.this.closeAction.run();
			}
		});
		loadWindowIcon();

		runtime.getIntifaceService().setConnectionListener(panel::updateConnection);
		runtime.getMusicSyncService().setListener(panel::updateMusicSync);
		sourceMessages.setListener(this::showSourceMessage);
	}

	public void show()
	{
		requireEventDispatchThread();
		frame.setVisible(true);
	}

	public DiscordJoinConsentHandler createDiscordJoinConsentHandler()
	{
		return new DiscordJoinConsentHandler()
		{
			@Override
			public void onDeepLinkOpened()
			{
				SwingUtilities.invokeLater(HapticScapeDesktopWindow.this::focusRemotePlay);
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
		};
	}

	private void focusRemotePlay()
	{
		frame.setVisible(true);
		frame.setState(JFrame.NORMAL);
		frame.toFront();
		frame.requestFocus();
		panel.showDiscordRemoteView();
	}

	private void connectToIntiface()
	{
		try
		{
			runtime.connectToIntiface();
		}
		catch (URISyntaxException exception)
		{
			panel.showInputError("Invalid Intiface server URI");
		}
	}

	private void sendTestPattern()
	{
		runtime.sendConfiguredPattern(
			HapticEventType.MANUAL_PREVIEW,
			panel.getPatternPreset(),
			"TEST_XP"
		);
	}

	private void sendTestLevelUpPattern()
	{
		runtime.sendConfiguredPattern(
			HapticEventType.MANUAL_PREVIEW,
			panel.getLevelUpPatternPreset(),
			"TEST_LEVEL_UP"
		);
	}

	private void previewLevel99Ceremony()
	{
		String skillId = panel.getSelectedProfileSkillId();
		runtime.startLevel99Ceremony(skillId == null ? "attack" : skillId, false);
	}

	private void sendTestSkillProfile()
	{
		String skillId = panel.getSelectedProfileSkillId();
		if (skillId == null)
		{
			return;
		}
		XpFeedbackSettings settings = panel.getXpFeedbackSettings(skillId);
		runtime.sendPattern(
			HapticEventType.MANUAL_PREVIEW,
			settings.getPatternSelection(),
			"TEST_SKILL_" + SkillIds.toConfigToken(skillId),
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void sendTestGenericNotificationPattern()
	{
		NotificationFeedbackSettings settings = panel.getNotificationFeedbackSettings();
		if (panel.isGenericNotificationClickEnabled())
		{
			runtime.playClick();
		}
		runtime.sendPattern(
			HapticEventType.MANUAL_PREVIEW,
			settings.getPatternSelection(),
			"TEST_ALERT_GENERIC_NOTIFICATION",
			settings.getIntensityPercent(),
			settings.getDurationMillis()
		);
	}

	private void sendTestAlert(AlertCategory category)
	{
		if (panel.isAlertClickEnabled(category))
		{
			runtime.playClick();
		}
		panel.getAlertProfiles()
			.resolve(category, panel.getNotificationFeedbackSettings())
			.ifPresent(playback -> runtime.sendPattern(
				HapticEventType.MANUAL_PREVIEW,
				playback.getPatternSelection(),
				"TEST_ALERT_" + category.name(),
				playback.getIntensityPercent(),
				playback.getDurationMillis()
			));
	}

	private void showSourceMessage(DesktopSourceMessageService.Message message)
	{
		SwingUtilities.invokeLater(() ->
		{
			sourceMessageLabel.setText(message.getText());
			Integer rgb = message.getRgb();
			sourceMessageLabel.setForeground(
				rgb == null ? new Color(220, 220, 220) : new Color(rgb)
			);
		});
	}

	private void loadWindowIcon()
	{
		java.net.URL resource = HapticScapeDesktopWindow.class.getResource("/hapticscape.png");
		if (resource == null)
		{
			return;
		}
		try
		{
			Image image = ImageIO.read(resource);
			if (image != null)
			{
				frame.setIconImage(image);
			}
		}
		catch (IOException ignored)
		{
			// An application icon is cosmetic only.
		}
	}

	private static void requireEventDispatchThread()
	{
		if (!EventQueue.isDispatchThread())
		{
			throw new IllegalStateException("Desktop window must be shown on the Swing event thread");
		}
	}

	@Override
	public void close()
	{
		sourceMessages.setListener(null);
		panel.close();
		frame.dispose();
	}
}
