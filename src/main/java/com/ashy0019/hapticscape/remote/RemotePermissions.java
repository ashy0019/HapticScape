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
	public static final int MINIMUM_LIVE_DURATION_MILLIS = 1_000;
	public static final int MAXIMUM_LIVE_DURATION_MILLIS = 300_000;
	public static final int DEFAULT_LIVE_DURATION_MILLIS = 30_000;

	private final int schemaVersion;
	private final boolean settingsAllowed;
	private final boolean hapticsAllowed;
	private final boolean liveHapticsAllowed;
	private final boolean clicksAllowed;
	private final boolean desktopNotificationsAllowed;
	private final boolean localChatboxMessagesAllowed;
	private final int maximumIntensityPercent;
	private final int maximumDurationMillis;
	private final int maximumLiveDurationMillis;

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
			settingsAllowed,
			hapticsAllowed,
			false,
			clicksAllowed,
			desktopNotificationsAllowed,
			localChatboxMessagesAllowed,
			maximumIntensityPercent,
			maximumDurationMillis,
			DEFAULT_LIVE_DURATION_MILLIS
		);
	}

	public RemotePermissions(
		boolean settingsAllowed,
		boolean hapticsAllowed,
		boolean liveHapticsAllowed,
		boolean clicksAllowed,
		boolean desktopNotificationsAllowed,
		boolean localChatboxMessagesAllowed,
		int maximumIntensityPercent,
		int maximumDurationMillis,
		int maximumLiveDurationMillis)
	{
		this(
			SCHEMA_VERSION,
			settingsAllowed,
			hapticsAllowed,
			liveHapticsAllowed,
			clicksAllowed,
			desktopNotificationsAllowed,
			localChatboxMessagesAllowed,
			maximumIntensityPercent,
			maximumDurationMillis,
			maximumLiveDurationMillis
		);
	}

	private RemotePermissions(
		int schemaVersion,
		boolean settingsAllowed,
		boolean hapticsAllowed,
		boolean liveHapticsAllowed,
		boolean clicksAllowed,
		boolean desktopNotificationsAllowed,
		boolean localChatboxMessagesAllowed,
		int maximumIntensityPercent,
		int maximumDurationMillis,
		int maximumLiveDurationMillis)
	{
		this.schemaVersion = schemaVersion;
		this.settingsAllowed = settingsAllowed;
		this.hapticsAllowed = hapticsAllowed;
		this.liveHapticsAllowed = liveHapticsAllowed;
		this.clicksAllowed = clicksAllowed;
		this.desktopNotificationsAllowed = desktopNotificationsAllowed;
		this.localChatboxMessagesAllowed = localChatboxMessagesAllowed;
		this.maximumIntensityPercent = maximumIntensityPercent;
		this.maximumDurationMillis = maximumDurationMillis;
		this.maximumLiveDurationMillis = maximumLiveDurationMillis;
		validate();
	}

	public static RemotePermissions defaults()
	{
		return new RemotePermissions(
			true, true, false, true, true, false,
			60, 3_000, DEFAULT_LIVE_DURATION_MILLIS
		);
	}

	static RemotePermissions none()
	{
		return new RemotePermissions(
			false, false, false, false, false, false,
			0, 50, 0
		);
	}

	public static RemotePermissions capture(HapticScapeConfig config)
	{
		Objects.requireNonNull(config, "config");
		return new RemotePermissions(
			config.remoteSettingsAllowed(),
			config.remoteHapticsAllowed(),
			config.remoteLiveHapticsAllowed(),
			config.remoteClicksAllowed(),
			config.remoteDesktopNotificationsAllowed(),
			config.remoteLocalChatboxMessagesAllowed(),
			clamp(config.remoteMaximumIntensityPercent(), 0, 100),
			clamp(
				config.remoteMaximumDurationMillis(),
				MINIMUM_DURATION_MILLIS,
				MAXIMUM_DURATION_MILLIS
			),
			clamp(
				config.remoteMaximumLiveDurationMillis(),
				MINIMUM_LIVE_DURATION_MILLIS,
				MAXIMUM_LIVE_DURATION_MILLIS
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
		if (maximumLiveDurationMillis != 0
			&& (maximumLiveDurationMillis < MINIMUM_LIVE_DURATION_MILLIS
				|| maximumLiveDurationMillis > MAXIMUM_LIVE_DURATION_MILLIS))
		{
			throw new IllegalArgumentException("Remote live duration limit is out of range");
		}
		if (liveHapticsAllowed && maximumLiveDurationMillis == 0)
		{
			throw new IllegalArgumentException("Remote live duration limit is required");
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

	public boolean isLiveHapticsAllowed()
	{
		return liveHapticsAllowed;
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

	public int getMaximumLiveDurationMillis()
	{
		return maximumLiveDurationMillis;
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
			&& liveHapticsAllowed == that.liveHapticsAllowed
			&& clicksAllowed == that.clicksAllowed
			&& desktopNotificationsAllowed == that.desktopNotificationsAllowed
			&& localChatboxMessagesAllowed == that.localChatboxMessagesAllowed
			&& maximumIntensityPercent == that.maximumIntensityPercent
			&& maximumDurationMillis == that.maximumDurationMillis
			&& maximumLiveDurationMillis == that.maximumLiveDurationMillis;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(
			schemaVersion,
			settingsAllowed,
			hapticsAllowed,
			liveHapticsAllowed,
			clicksAllowed,
			desktopNotificationsAllowed,
			localChatboxMessagesAllowed,
			maximumIntensityPercent,
			maximumDurationMillis,
			maximumLiveDurationMillis
		);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
