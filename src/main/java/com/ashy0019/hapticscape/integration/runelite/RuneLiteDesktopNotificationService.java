package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.host.DesktopNotificationService;
import java.awt.TrayIcon;
import java.util.Objects;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.config.RuneLiteConfig;

/** RuneLite-hosted desktop notification implementation. */
public final class RuneLiteDesktopNotificationService implements DesktopNotificationService
{
	private final Notifier notifier;
	private final RuneLiteConfig config;

	public RuneLiteDesktopNotificationService(Notifier notifier, RuneLiteConfig config)
	{
		this.notifier = Objects.requireNonNull(notifier, "notifier");
		this.config = Objects.requireNonNull(config, "config");
	}

	@Override
	public void notify(String message)
	{
		Notification notification = new Notification(
			true,
			true,
			true,
			config.enableTrayNotifications(),
			TrayIcon.MessageType.NONE,
			config.notificationRequestFocus(),
			config.notificationSound(),
			null,
			config.notificationVolume(),
			config.notificationTimeout(),
			false,
			config.flashNotification(),
			config.notificationFlashColor(),
			config.sendNotificationsWhenFocused()
		);
		notifier.notify(notification, message);
	}
}
