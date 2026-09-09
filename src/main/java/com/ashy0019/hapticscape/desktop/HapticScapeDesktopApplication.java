package com.ashy0019.hapticscape.desktop;

import com.ashy0019.hapticscape.HapticScapeRuntime;
import com.ashy0019.hapticscape.HapticScapeRuntimeDependencies;
import com.ashy0019.hapticscape.HapticScapeSettingsSource;
import com.ashy0019.hapticscape.SettingsBackedHapticScapeSettings;
import com.ashy0019.hapticscape.SkillCatalog;
import com.ashy0019.hapticscape.integration.desktop.AwtDesktopNotificationService;
import com.ashy0019.hapticscape.integration.desktop.DesktopAudioCaptureSources;
import com.ashy0019.hapticscape.integration.desktop.DesktopDiscordDeepLinkInbox;
import com.ashy0019.hapticscape.integration.desktop.DesktopSecretProtectors;
import com.ashy0019.hapticscape.integration.desktop.DesktopSourceMessageService;
import com.ashy0019.hapticscape.integration.desktop.DesktopStoragePaths;
import com.ashy0019.hapticscape.integration.desktop.JavaSoundPlayer;
import com.ashy0019.hapticscape.integration.osrs.OldSchoolRuneScapeSkillCatalog;
import com.ashy0019.hapticscape.protocol.LocalhostTransportEndpoint;
import com.ashy0019.hapticscape.remote.DiscordDeepLinkInbox;
import com.ashy0019.hapticscape.remote.DiscordPairingBridge;
import com.ashy0019.hapticscape.remote.SettingsStore;
import com.ashy0019.hapticscape.remote.RemoteSessionListener;
import com.ashy0019.hapticscape.remote.RemoteSessionSnapshot;
import com.ashy0019.hapticscape.remote.SettingsLockCatalog;
import com.ashy0019.hapticscape.storage.FileSettingsStore;
import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import com.google.gson.Gson;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import okhttp3.OkHttpClient;

/** Owns the standalone desktop host lifecycle around the neutral HapticScape runtime. */
public final class HapticScapeDesktopApplication implements AutoCloseable
{
	private final AtomicBoolean closed = new AtomicBoolean();
	private OkHttpClient httpClient;
	private AwtDesktopNotificationService desktopNotifications;
	private HapticScapeRuntime runtime;
	private HapticScapeDesktopWindow window;
	private DiscordDeepLinkInbox deepLinkInbox;
	private ProtectedExitAuditStore protectedExitAudit;

	public void start()
	{
		if (runtime != null)
		{
			return;
		}

		SkillCatalog skillCatalog = OldSchoolRuneScapeSkillCatalog.get();
		HapticScapeStoragePaths storagePaths = DesktopStoragePaths.hapticScapeStoragePaths();
		SettingsStore settingsStore = new FileSettingsStore(storagePaths.getSettingsPath());
		HapticScapeSettingsSource settings = new SettingsBackedHapticScapeSettings(
			settingsStore,
			skillCatalog
		);
		httpClient = new OkHttpClient();
		Gson gson = new Gson();
		desktopNotifications = new AwtDesktopNotificationService("HapticScape");
		DesktopSourceMessageService sourceMessages = new DesktopSourceMessageService();
		protectedExitAudit = new ProtectedExitAuditStore(
			storagePaths.getProtectedExitStatePath()
		);

		runtime = new HapticScapeRuntime(new HapticScapeRuntimeDependencies(
			httpClient,
			gson,
			settings,
			settingsStore,
			skillCatalog,
			storagePaths,
			new JavaSoundPlayer(),
			desktopNotifications,
			sourceMessages,
			DesktopAudioCaptureSources::systemOutput,
			DesktopSecretProtectors.savedUnlockKeys(),
			DesktopSecretProtectors.discordCredentials(),
			LocalhostTransportEndpoint.DEFAULT_PORT
		));

		try
		{
			runtime.start();
			protectedExitAudit.beginRun(
				runtime.getSettingsLockService().isLocked(SettingsLockCatalog.PROTECTED_EXIT)
			);
			runtime.getSettingsLockService().addListener(snapshot ->
				protectedExitAudit.setProtectionActive(
					snapshot.isLocked(SettingsLockCatalog.PROTECTED_EXIT)
				)
			);
			wireProtectedExitAudit();
			createAndShowWindow(settings, skillCatalog, settingsStore, sourceMessages);
			wireDiscordDeepLinks();
		}
		catch (RuntimeException failure)
		{
			if (protectedExitAudit != null)
			{
				protectedExitAudit.markAuthorizedEnd();
			}
			close();
			throw failure;
		}
	}

	private void wireProtectedExitAudit()
	{
		runtime.getRemoteSessionManager().addListener(new RemoteSessionListener()
		{
			@Override
			public void onRemoteSessionChanged(RemoteSessionSnapshot snapshot)
			{
				if (protectedExitAudit.hasPendingUnauthorizedEnd()
					&& runtime.getRemoteSessionManager().reportUnauthorizedEnd(
						"Unauthorized end"
					))
				{
					protectedExitAudit.clearPendingUnauthorizedEnd();
				}
			}

			@Override
			public void onUnauthorizedEnd(String reason)
			{
				desktopNotifications.notify("Unauthorized end");
			}
		});
	}

	private void createAndShowWindow(
		HapticScapeSettingsSource settings,
		SkillCatalog skillCatalog,
		SettingsStore settingsStore,
		DesktopSourceMessageService sourceMessages)
	{
		Runnable create = () ->
		{
			window = new HapticScapeDesktopWindow(
				runtime,
				settings,
				skillCatalog,
				settingsStore,
				sourceMessages,
				protectedExitAudit,
				this::closeAndExit
			);
			window.show();
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			create.run();
			return;
		}
		try
		{
			SwingUtilities.invokeAndWait(create);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while opening HapticScape", interrupted);
		}
		catch (InvocationTargetException failure)
		{
			Throwable cause = failure.getCause();
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException("Unable to open HapticScape", cause);
		}
	}

	private void wireDiscordDeepLinks()
	{
		DiscordPairingBridge bridge = runtime.getDiscordPairingBridge();
		bridge.setJoinConsentHandler(window.createDiscordJoinConsentHandler());
		deepLinkInbox = DesktopDiscordDeepLinkInbox.getInstance();
		deepLinkInbox.start();
		deepLinkInbox.setHandler(bridge::acceptDeepLink);
	}

	private void closeAndExit()
	{
		close();
		System.exit(0);
	}

	@Override
	public void close()
	{
		if (!closed.compareAndSet(false, true))
		{
			return;
		}
		if (deepLinkInbox != null)
		{
			deepLinkInbox.setHandler(null);
			deepLinkInbox = null;
		}
		HapticScapeDesktopWindow currentWindow = window;
		window = null;
		if (currentWindow != null)
		{
			if (SwingUtilities.isEventDispatchThread())
			{
				currentWindow.close();
			}
			else
			{
				SwingUtilities.invokeLater(currentWindow::close);
			}
		}
		if (runtime != null)
		{
			runtime.close();
			runtime = null;
		}
		if (desktopNotifications != null)
		{
			desktopNotifications.close();
			desktopNotifications = null;
		}
		if (httpClient != null)
		{
			httpClient.dispatcher().executorService().shutdown();
			httpClient.connectionPool().evictAll();
			httpClient = null;
		}
	}
}
