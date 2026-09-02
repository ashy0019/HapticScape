package com.ashy0019.hapticscape.rogue.feedback;

import com.ashy0019.hapticscape.device.HapticEventType;
import com.ashy0019.hapticscape.device.HapticPattern;
import com.ashy0019.hapticscape.device.HapticRequest;
import com.ashy0019.hapticscape.rogue.RogueFeedbackEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Maps semantic casino events onto the existing HapticScape playback system. */
public final class CasinoFeedbackMapper
{
	private CasinoFeedbackMapper()
	{
	}

	public static HapticRequest toRequest(RogueFeedbackEvent event, double scale)
	{
		Objects.requireNonNull(event, "event");
		double safeScale = clamp(scale);
		List<HapticPattern.Step> steps = new ArrayList<>();
		HapticEventType eventType;

		switch (event)
		{
			case UNLOCK:
				eventType = HapticEventType.ROGUE_UNLOCK;
				pulse(steps, safeScale, 0.45, 70);
				gap(steps, 35);
				pulse(steps, safeScale, 0.65, 90);
				gap(steps, 35);
				pulse(steps, safeScale, 0.90, 140);
				break;
			case DEAL:
				eventType = HapticEventType.BLACKJACK_ACTION;
				pulse(steps, safeScale, 0.25, 55);
				break;
			case HIT:
				eventType = HapticEventType.BLACKJACK_ACTION;
				pulse(steps, safeScale, 0.35, 50);
				gap(steps, 40);
				pulse(steps, safeScale, 0.45, 65);
				break;
			case STAND:
				eventType = HapticEventType.BLACKJACK_ACTION;
				pulse(steps, safeScale, 0.35, 100);
				break;
			case DOUBLE:
				eventType = HapticEventType.BLACKJACK_ACTION;
				pulse(steps, safeScale, 0.45, 70);
				gap(steps, 45);
				pulse(steps, safeScale, 0.75, 110);
				break;
			case WIN:
				eventType = HapticEventType.BLACKJACK_RESULT;
				pulse(steps, safeScale, 0.35, 70);
				gap(steps, 35);
				pulse(steps, safeScale, 0.60, 85);
				gap(steps, 35);
				pulse(steps, safeScale, 0.85, 120);
				break;
			case LOSS:
				eventType = HapticEventType.BLACKJACK_RESULT;
				pulse(steps, safeScale, 0.75, 110);
				gap(steps, 45);
				pulse(steps, safeScale, 0.45, 90);
				gap(steps, 45);
				pulse(steps, safeScale, 0.20, 70);
				break;
			case BUST:
				eventType = HapticEventType.BLACKJACK_RESULT;
				pulse(steps, safeScale, 0.90, 120);
				gap(steps, 40);
				pulse(steps, safeScale, 0.20, 80);
				break;
			case PUSH:
				eventType = HapticEventType.BLACKJACK_RESULT;
				pulse(steps, safeScale, 0.40, 75);
				gap(steps, 65);
				pulse(steps, safeScale, 0.40, 75);
				break;
			case BLACKJACK:
				eventType = HapticEventType.BLACKJACK_NATURAL;
				pulse(steps, safeScale, 0.40, 65);
				gap(steps, 30);
				pulse(steps, safeScale, 0.60, 75);
				gap(steps, 30);
				pulse(steps, safeScale, 0.80, 90);
				gap(steps, 35);
				pulse(steps, safeScale, 1.00, 180);
				break;
			default:
				throw new IllegalArgumentException("Unknown Rogue feedback event: " + event);
		}

		return new HapticRequest(eventType, new HapticPattern(steps));
	}

	private static void pulse(
		List<HapticPattern.Step> steps,
		double scale,
		double intensity,
		long millis)
	{
		steps.add(new HapticPattern.Step(
			clamp(scale * intensity),
			Duration.ofMillis(millis)
		));
	}

	private static void gap(List<HapticPattern.Step> steps, long millis)
	{
		steps.add(new HapticPattern.Step(0.0, Duration.ofMillis(millis)));
	}

	private static double clamp(double value)
	{
		if (!Double.isFinite(value))
		{
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
