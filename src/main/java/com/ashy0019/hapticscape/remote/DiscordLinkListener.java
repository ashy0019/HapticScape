package com.ashy0019.hapticscape.remote;

@FunctionalInterface
public interface DiscordLinkListener
{
	void onDiscordLinkChanged(DiscordLinkSnapshot snapshot);
}
