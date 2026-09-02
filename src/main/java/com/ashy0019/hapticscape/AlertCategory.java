package com.ashy0019.hapticscape;

public enum AlertCategory
{
	DIRECT_MESSAGE("Direct message", null),
	TRADE_REQUEST("Trade request", null),
	LOW_HITPOINTS("Low hitpoints", AlertTriggerParameter.HITPOINTS),
	LOW_PRAYER("Low prayer", AlertTriggerParameter.PRAYER),
	VALUABLE_DROP("Valuable drop", AlertTriggerParameter.LOOT_VALUE),
	INVENTORY_FULL("Inventory full", null),
	POISONED_OR_VENOMED("Poisoned / venomed", null),
	SPECIAL_ATTACK_READY("Special attack ready", AlertTriggerParameter.SPECIAL_ENERGY),
	PLAYER_DEATH("Player death", null);

	private final String displayName;
	private final AlertTriggerParameter triggerParameter;

	AlertCategory(String displayName, AlertTriggerParameter triggerParameter)
	{
		this.displayName = displayName;
		this.triggerParameter = triggerParameter;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public boolean hasTriggerParameter()
	{
		return triggerParameter != null;
	}

	public AlertTriggerParameter getTriggerParameter()
	{
		if (triggerParameter == null)
		{
			throw new IllegalStateException(name() + " does not have a trigger parameter");
		}
		return triggerParameter;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
