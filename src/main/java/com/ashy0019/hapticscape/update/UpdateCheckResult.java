package com.ashy0019.hapticscape.update;

public final class UpdateCheckResult
{
	private final String latestVersion;
	private final boolean newer;
	private final String errorMessage;

	private UpdateCheckResult(String latestVersion, boolean newer, String errorMessage)
	{
		this.latestVersion = latestVersion;
		this.newer = newer;
		this.errorMessage = errorMessage;
	}

	static UpdateCheckResult success(String latestVersion, boolean newer)
	{
		return new UpdateCheckResult(latestVersion, newer, null);
	}

	static UpdateCheckResult failure(String errorMessage)
	{
		return new UpdateCheckResult(null, false, errorMessage);
	}

	public String getLatestVersion()
	{
		return latestVersion;
	}

	public boolean isNewer()
	{
		return newer;
	}

	public boolean isFailure()
	{
		return errorMessage != null;
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}
}
