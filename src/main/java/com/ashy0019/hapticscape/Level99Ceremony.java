package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.device.HapticPattern;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The fixed Level 99 ceremony shared by haptic playback and the visual pulse.
 */
public final class Level99Ceremony
{
	public static final String CHAT_MESSAGE =
		"CONGRATULATIONS, YOUR BUTT HAS ACHIEVED MASTERY.";
	public static final int PHRASE_REPETITIONS = 11;
	public static final int SHORT_BEATS_PER_PHRASE = 8;

	private static final double SHORT_INTENSITY = 0.60;
	private static final double LONG_INTENSITY = 1.0;
	private static final Duration SHORT_BEAT = Duration.ofMillis(55);
	private static final Duration SHORT_GAP = Duration.ofMillis(35);
	private static final Duration LONG_BEAT = Duration.ofMillis(260);
	private static final Duration PHRASE_GAP = Duration.ofMillis(120);
	private static final long PHRASE_DURATION_NANOS =
		SHORT_BEATS_PER_PHRASE * (SHORT_BEAT.toNanos() + SHORT_GAP.toNanos())
			+ LONG_BEAT.toNanos()
			+ PHRASE_GAP.toNanos();
	private static final HapticPattern PATTERN = createPattern();
	private static final long TOTAL_DURATION_NANOS = PATTERN.getSteps().stream()
		.map(HapticPattern.Step::getDuration)
		.mapToLong(Duration::toNanos)
		.sum();

	private Level99Ceremony()
	{
	}

	public static HapticPattern pattern()
	{
		return PATTERN;
	}

	public static long totalDurationNanos()
	{
		return TOTAL_DURATION_NANOS;
	}

	public static long phraseDurationNanos()
	{
		return PHRASE_DURATION_NANOS;
	}

	public static double intensityAt(long elapsedNanos)
	{
		if (elapsedNanos < 0 || elapsedNanos >= TOTAL_DURATION_NANOS)
		{
			return 0.0;
		}

		long remaining = elapsedNanos;
		for (HapticPattern.Step step : PATTERN.getSteps())
		{
			long stepNanos = step.getDuration().toNanos();
			if (remaining < stepNanos)
			{
				return step.getIntensity();
			}
			remaining -= stepNanos;
		}
		return 0.0;
	}

	private static HapticPattern createPattern()
	{
		List<HapticPattern.Step> phrase = new ArrayList<>();
		for (int beat = 0; beat < SHORT_BEATS_PER_PHRASE; beat++)
		{
			phrase.add(new HapticPattern.Step(SHORT_INTENSITY, SHORT_BEAT));
			phrase.add(new HapticPattern.Step(0.0, SHORT_GAP));
		}
		phrase.add(new HapticPattern.Step(LONG_INTENSITY, LONG_BEAT));
		phrase.add(new HapticPattern.Step(0.0, PHRASE_GAP));
		return new HapticPattern(phrase).repeated(PHRASE_REPETITIONS);
	}
}
