package com.ashy0019.hapticscape.ui;

/** Pure disconnected-vault summary state. */
final class SavedUnlockKeyVaultState
{
	private final String status;
	private final boolean manageable;

	private SavedUnlockKeyVaultState(String status, boolean manageable)
	{
		this.status = status;
		this.manageable = manageable;
	}

	static SavedUnlockKeyVaultState from(
		boolean available,
		String unavailableMessage,
		int count)
	{
		if (!available)
		{
			return new SavedUnlockKeyVaultState(
				unavailableMessage == null || unavailableMessage.trim().isEmpty()
					? "Saved Unlock Keys are unavailable"
					: unavailableMessage,
				false
			);
		}
		if (count <= 0)
		{
			return new SavedUnlockKeyVaultState(
				"No accepted unlock keys saved",
				false
			);
		}
		return new SavedUnlockKeyVaultState(
			count + (count == 1 ? " saved unlock key" : " saved unlock keys"),
			true
		);
	}

	String getStatus()
	{
		return status;
	}

	boolean isManageable()
	{
		return manageable;
	}
}
