package com.ashy0019.hapticscape.device;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable sequence of vibration intensities and their durations.
 */
public final class HapticPattern
{
	private final List<Step> steps;

	public HapticPattern(List<Step> steps)
	{
		Objects.requireNonNull(steps, "steps");
		if (steps.isEmpty())
		{
			throw new IllegalArgumentException("A haptic pattern must contain at least one step");
		}

		List<Step> copiedSteps = new ArrayList<>(steps.size());
		for (Step step : steps)
		{
			copiedSteps.add(Objects.requireNonNull(step, "step"));
		}
		this.steps = Collections.unmodifiableList(copiedSteps);
	}

	public static HapticPattern single(double intensity, Duration duration)
	{
		return new HapticPattern(Collections.singletonList(new Step(intensity, duration)));
	}

	public List<Step> getSteps()
	{
		return steps;
	}

	public HapticPattern repeated(int repetitions)
	{
		if (repetitions < 1)
		{
			throw new IllegalArgumentException("A haptic pattern must play at least once");
		}
		if (repetitions == 1)
		{
			return this;
		}

		List<Step> repeatedSteps = new ArrayList<>(steps.size() * repetitions);
		for (int repetition = 0; repetition < repetitions; repetition++)
		{
			repeatedSteps.addAll(steps);
		}
		return new HapticPattern(repeatedSteps);
	}

	public static final class Step
	{
		private final double intensity;
		private final Duration duration;

		public Step(double intensity, Duration duration)
		{
			if (!Double.isFinite(intensity) || intensity < 0.0 || intensity > 1.0)
			{
				throw new IllegalArgumentException("Step intensity must be between 0.0 and 1.0");
			}

			this.duration = Objects.requireNonNull(duration, "duration");
			if (duration.isZero() || duration.isNegative())
			{
				throw new IllegalArgumentException("Step duration must be positive");
			}

			this.intensity = intensity;
		}

		public double getIntensity()
		{
			return intensity;
		}

		public Duration getDuration()
		{
			return duration;
		}
	}
}
