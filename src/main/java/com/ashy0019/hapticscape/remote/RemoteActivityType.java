package com.ashy0019.hapticscape.remote;

/** Explicit allowlist of gameplay facts which may cross a Remote Play session. */
public enum RemoteActivityType
{
	XP_GAIN,
	LEVEL_UP,
	MILESTONE,
	RESOURCE_CHANGED,
	INVENTORY_FULL,
	STATUS_CHANGED,
	LOOT_RECEIVED,
	PLAYER_DEATH,
	TRADE_REQUEST,
	DIRECT_MESSAGE
}
