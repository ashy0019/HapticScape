package com.ashy0019.hapticscape.remote;

import com.ashy0019.hapticscape.HapticScapeConfig;
import java.util.Objects;

/**
 * Participant-owned permissions for Remote Control actions.
 *
 * <p>These values are deliberately not part of {@link RemoteSettingsSnapshot}.
 * A controller may observe them so its UI can explain unavailable controls,
 * but only the participant can change or enforce them.</p>
 */
public final class RemotePermissions
{
	public static final int SCHEMA_VERSION = 1;
	public static final int MINIMUM_DURATION_MILLIS = 50;
	public static final int MAXIMUM_DURATION_MILLIS = 10_000;

	private final int schemaVersion;
	private final boolean settingsAllowed;
	private final boolean hapticsAllowed;
	private final boolean clicksAllowed;
	private final boolean desktopNotificationsAllowed;
	private final boolean localChatboxMessagesAllowed;
	private final int maximumIntensityPercent;
	private final int maximumDurationMillis;

	public RemotePermissions(
		boolean settingsAllowed,
		boolean hapticsAllowed,
		boolean clicksAllowed,
		boolean desktopNotificationsAllowed,
		boolean localChatboxMessagesAllowed,
		int maximumIntensityPercent,
		int maximumDurationMillis)
	{
		this(
			SCHEMA_VERSION,
			settingsAllowed,
			hapticsAllowed,
			clicksAllowed,
			desktopNotificationsAllowed,
			localChatboxMessagesAllowed,
			maximumIntensityPercent,
			maximumDurationMillis
		);
	}

	private RemotePermissions(
		int schemaVersion,
		boolean settingsAllowed,
		boolean hapticsAllowed,
		boolean clicksAllowed,
		boolean desktopNotificationsAllowed,
		boolean localChatboxMessagesAllowed,
		int maximumIntensityPercent,
		int maximumDurationMillis)
	{
		this.schemaVersion = schemaVersion;
		this.settingsAllowed = settingsAllowed;
		this.hapticsAllowed = hapticsAllowed;
		this.clicksAllowed = clicksAllowed;
		this.desktopNotificationsAllowed = desktopNotificationsAllowed;
		this.localChatboxMessagesAllowed = localChatboxMessagesAllowed;
		this.maximumIntensityPercent = maximumIntensityPercent;
		this.maximumDurationMillis = maximumDurationMillis;
		validate();
	}

	public static RemotePermissions defaults()
	{
		return new RemotePermissions(true, true, true, true, false, 60, 3_000);
	}

	static RemotePermissions none()
	{
		return new RemotePermissions(false, false, false, false, false, 0, 50);
	}

	public static RemotePermissions capture(HapticScapeConfig config)
	{
		Objects.requireNonNull(config, "config");
		return new RemotePermissions(
			config.remoteSettingsAllowed(),
			config.remoteHapticsAllowed(),
			config.remoteClicksAllowed(),
			config.remoteDesktopNotificationsAllowed(),
			config.remoteLocalChatboxMessagesAllowed(),
			clamp(config.remoteMaximumIntensityPercent(), 0, 100),
			clamp(
				config.remoteMaximumDurationMillis(),
				MINIMUM_DURATION_MILLIS,
				MAXIMUM_DURATION_MILLIS
			)
		);
	}

	public void validate()
	{
		if (schemaVersion != SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported remote-permissions schema");
		}
		if (maximumIntensityPercent < 0 || maximumIntensityPercent > 100)
		{
			throw new IllegalArgumentException("Remote intensity limit is out of range");
		}
		if (maximumDurationMillis < MINIMUM_DURATION_MILLIS
			|| maximumDurationMillis > MAXIMUM_DURATION_MILLIS)
		{
			throw new IllegalArgumentException("Remote duration limit is out of range");
		}
	}

	public boolean isSettingsAllowed()
	{
		return settingsAllowed;
	}

	public boolean isHapticsAllowed()
	{
		return hapticsAllowed;
	}

	public boolean isClicksAllowed()
	{
		return clicksAllowed;
	}

	public boolean isDesktopNotificationsAllowed()
	{
		return desktopNotificationsAllowed;
	}

	public boolean isLocalChatboxMessagesAllowed()
	{
		return localChatboxMessagesAllowed;
	}

	public int getMaximumIntensityPercent()
	{
		return maximumIntensityPercent;
	}

	public int getMaximumDurationMillis()
	{
		return maximumDurationMillis;
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof RemotePermissions))
		{
			return false;
		}
		RemotePermissions that = (RemotePermissions) other;
		return schemaVersion == that.schemaVersion
			&& settingsAllowed == that.settingsAllowed
			&& hapticsAllowed == that.hapticsAllowed
			&& clicksAllowed == that.clicksAllowed
			&& desktopNotificationsAllowed == that.desktopNotificationsAllowed
			&& localChatboxMessagesAllowed == that.localChatboxMessagesAllowed
			&& maximumIntensityPercent == that.maximumIntensityPercent
			&& maximumDurationMillis == that.maximumDurationMillis;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(
			schemaVersion,
			settingsAllowed,
			hapticsAllowed,
			clicksAllowed,
			desktopNotificationsAllowed,
			localChatboxMessagesAllowed,
			maximumIntensityPercent,
			maximumDurationMillis
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
