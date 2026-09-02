package com.ashy0019.hapticscape.clicker;

/**
 * Starts one click sound at the requested gain.
 */
@FunctionalInterface
public interface ClickPlayback
{
	void play(float gainDb) throws Exception;
}
