package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.remote.RemotePermissions;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Read-only summary of the limits the participant currently enforces. */
final class RemotePermissionSummaryPanel extends JPanel
{
	private final WrappedTextLabel summary = new WrappedTextLabel("");

	RemotePermissionSummaryPanel()
	{
		setName("remotePermissionSummary");
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBorder(PanelUi.createSectionBorder("Partner permissions"));
		summary.setName("remotePermissionSummaryText");
		PanelUi.addFlexibleVerticalComponent(this, summary);
	}

	void apply(RemotePermissions permissions)
	{
		String text = describe(permissions);
		summary.setPlainText(text);
		summary.setToolTipText(text.replace('\n', ' '));
		revalidate();
		repaint();
	}

	static String describe(RemotePermissions permissions)
	{
		return "Settings: " + allowed(permissions.isSettingsAllowed())
			+ "\nHaptics: " + (permissions.isHapticsAllowed()
				? "Up to " + permissions.getMaximumIntensityPercent() + "% for "
					+ duration(permissions.getMaximumDurationMillis())
				: "Blocked")
			+ "\nLive Forge: " + (permissions.isLiveHapticsAllowed()
				? "Up to " + duration(permissions.getMaximumLiveDurationMillis())
				: "Blocked")
			+ "\nClicks: " + allowed(permissions.isClicksAllowed())
			+ "\nMessages: " + messageDestinations(permissions)
			+ "\nLive activity: " + allowed(permissions.isActivitySharingAllowed())
			+ "\nProtected startup/exit: " + allowed(permissions.isProtectedExitAllowed());
	}

	private static String messageDestinations(RemotePermissions permissions)
	{
		return permissions.isDesktopNotificationsAllowed()
			? "Desktop notifications"
			: "Blocked";
	}

	private static String allowed(boolean value)
	{
		return value ? "Allowed" : "Blocked";
	}

	private static String duration(int millis)
	{
		if (millis >= 1_000 && millis % 1_000 == 0)
		{
			return (millis / 1_000) + " s";
		}
		return millis + " ms";
	}
}
