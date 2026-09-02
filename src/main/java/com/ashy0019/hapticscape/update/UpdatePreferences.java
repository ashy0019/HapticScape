package com.ashy0019.hapticscape.update;

public final class UpdatePreferences
{
	private final Boolean automaticUpdates;
	private final Boolean updateNotifications;
	private final String skippedVersion;
	private final String lastCheckUtc;
	private final boolean forceCheck;

	public UpdatePreferences(
		Boolean automaticUpdates,
		Boolean updateNotifications,
		String skippedVersion,
		String lastCheckUtc,
		boolean forceCheck)
	{
		this.automaticUpdates = automaticUpdates;
		this.updateNotifications = updateNotifications;
		this.skippedVersion = emptyToNull(skippedVersion);
		this.lastCheckUtc = emptyToNull(lastCheckUtc);
		this.forceCheck = forceCheck;
	}

	public static UpdatePreferences defaults()
	{
		return new UpdatePreferences(false, true, null, null, false);
	}

	public boolean isAutomaticUpdates()
	{
		return Boolean.TRUE.equals(automaticUpdates);
	}

	public boolean isUpdateNotifications()
	{
		return updateNotifications == null || updateNotifications;
	}

	public String getSkippedVersion()
	{
		return skippedVersion;
	}

	public String getLastCheckUtc()
	{
		return lastCheckUtc;
	}

	public boolean isForceCheck()
	{
		return forceCheck;
	}

	public UpdatePreferences withAutomaticUpdates(boolean enabled)
	{
		return new UpdatePreferences(
			enabled,
			isUpdateNotifications(),
			skippedVersion,
			lastCheckUtc,
			forceCheck);
	}

	public UpdatePreferences withUpdateNotifications(boolean enabled)
	{
		return new UpdatePreferences(
			isAutomaticUpdates(),
			enabled,
			skippedVersion,
			lastCheckUtc,
			forceCheck);
	}

	public UpdatePreferences requestUpdateOnNextLaunch()
	{
		return new UpdatePreferences(
			isAutomaticUpdates(),
			isUpdateNotifications(),
			null,
			null,
			true);
	}

	private static String emptyToNull(String value)
	{
		return value == null || value.trim().isEmpty() ? null : value;
	}
}
