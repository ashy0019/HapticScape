package com.ashy0019.hapticscape.remote;

import java.util.Objects;

/** The participant's terminal result for one remote action id. */
public final class RemoteActionAcknowledgement
{
	private final String actionId;
	private final RemoteActionResult result;
	private final String message;
	private final int appliedIntensityPercent;
	private final int appliedDurationMillis;

	RemoteActionAcknowledgement(
		String actionId,
		RemoteActionResult result,
		String message,
		int appliedIntensityPercent,
		int appliedDurationMillis)
	{
		this.actionId = Objects.requireNonNull(actionId, "actionId");
		this.result = Objects.requireNonNull(result, "result");
		this.message = message == null ? "" : message;
		this.appliedIntensityPercent = Math.max(0, appliedIntensityPercent);
		this.appliedDurationMillis = Math.max(0, appliedDurationMillis);
	}

	public void validate()
	{
		if (actionId == null || actionId.isEmpty() || actionId.length() > 64
			|| result == null || message == null || message.length() > 160
			|| appliedIntensityPercent > 100
			|| appliedDurationMillis > RemotePermissions.MAXIMUM_DURATION_MILLIS)
		{
			throw new IllegalArgumentException("Remote action acknowledgement is invalid");
		}
	}

	public String getActionId() { return actionId; }
	public RemoteActionResult getResult() { return result; }
	public String getMessage() { return message; }
	public int getAppliedIntensityPercent() { return appliedIntensityPercent; }
	public int getAppliedDurationMillis() { return appliedDurationMillis; }
}
