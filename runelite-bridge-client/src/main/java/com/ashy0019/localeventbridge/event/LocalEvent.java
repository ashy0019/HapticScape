package com.ashy0019.localeventbridge.event;

/**
 * Source-neutral event emitted by a local source integration.
 */
public interface LocalEvent
{
	String getSource();

	String getType();
}
