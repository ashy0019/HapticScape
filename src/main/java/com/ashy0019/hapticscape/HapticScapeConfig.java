package com.ashy0019.hapticscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(HapticScapeConfig.GROUP)
public interface HapticScapeConfig extends Config
{
	String GROUP = "hapticscape";
	String INTENSITY_PERCENT_KEY = "intensityPercent";
	String PULSE_DURATION_MILLIS_KEY = "pulseDurationMillis";

	@ConfigItem(
		keyName = "intifaceServer",
		name = "Intiface server",
		description = "WebSocket URI for the Intiface server",
		position = 0,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default String intifaceServer()
	{
		return "ws://localhost:12345";
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "minimumXpGain",
		name = "Minimum XP gain",
		description = "Minimum XP gained by one stat change before feedback is triggered",
		position = 1
	)
	default int minimumXpGain()
	{
		return 1;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = INTENSITY_PERCENT_KEY,
		name = "Intensity",
		description = "Device intensity as a percentage",
		position = 2,
		hidden = true
	)
	default int intensityPercent()
	{
		return 50;
	}

	@Range(min = 50, max = 10_000)
	@ConfigItem(
		keyName = PULSE_DURATION_MILLIS_KEY,
		name = "Pulse duration",
		description = "Feedback duration in milliseconds",
		position = 3,
		hidden = true
	)
	default int pulseDurationMillis()
	{
		return 500;
	}
}
