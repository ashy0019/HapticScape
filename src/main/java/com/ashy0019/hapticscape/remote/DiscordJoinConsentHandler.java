package com.ashy0019.hapticscape.remote;

import java.util.concurrent.CompletableFuture;

/** Brings the participant UI forward without ever granting remote authority itself. */
public interface DiscordJoinConsentHandler
{
	void onDeepLinkOpened();

	CompletableFuture<Boolean> requestConsent(DiscordJoinRequest request);

	void showError(String message);
}
