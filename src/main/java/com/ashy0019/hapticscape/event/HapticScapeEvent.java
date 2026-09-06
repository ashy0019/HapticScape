package com.ashy0019.hapticscape.event;

/**
 * Source-neutral event emitted by an integration and consumed by HapticScape.
 */
public interface HapticScapeEvent
{
	String getSource();

	String getType();
}
