package com.ashy0019.hapticscape;

import java.util.Objects;
import java.util.function.LongSupplier;
import net.runelite.api.Skill;

/**
 * Holds the small amount of time-based state consumed by the Level 99 overlay.
 */
public final class Level99CelebrationController
{
	private final LongSupplier nanoTime;
	private volatile ActiveCelebration activeCelebration;

	public Level99CelebrationController()
	{
		this(System::nanoTime);
	}

	Level99CelebrationController(LongSupplier nanoTime)
	{
		this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
	}

	public void start(Skill skill)
	{
		activeCelebration = new ActiveCelebration(
			Objects.requireNonNull(skill, "skill"),
			nanoTime.getAsLong()
		);
	}

	public void reset()
	{
		activeCelebration = null;
	}

	public Snapshot snapshot()
	{
		ActiveCelebration current = activeCelebration;
		if (current == null)
		{
			return Snapshot.inactive();
		}

		long elapsedNanos = Math.max(0L, nanoTime.getAsLong() - current.startedAtNanos);
		if (elapsedNanos >= Level99Ceremony.totalDurationNanos())
		{
			if (activeCelebration == current)
			{
				activeCelebration = null;
			}
			return Snapshot.inactive();
		}

		return Snapshot.active(
			current.skill,
			elapsedNanos,
			Level99Ceremony.intensityAt(elapsedNanos),
			(double) elapsedNanos / Level99Ceremony.totalDurationNanos()
		);
	}

	private static final class ActiveCelebration
	{
		private final Skill skill;
		private final long startedAtNanos;

		private ActiveCelebration(Skill skill, long startedAtNanos)
		{
			this.skill = skill;
			this.startedAtNanos = startedAtNanos;
		}
	}

	public static final class Snapshot
	{
		private static final Snapshot INACTIVE = new Snapshot(false, null, 0L, 0.0, 0.0);

		private final boolean active;
		private final Skill skill;
		private final long elapsedNanos;
		private final double pulseIntensity;
		private final double progress;

		private Snapshot(
			boolean active,
			Skill skill,
			long elapsedNanos,
			double pulseIntensity,
			double progress)
		{
			this.active = active;
			this.skill = skill;
			this.elapsedNanos = elapsedNanos;
			this.pulseIntensity = pulseIntensity;
			this.progress = progress;
		}

		private static Snapshot inactive()
		{
			return INACTIVE;
		}

		private static Snapshot active(
			Skill skill,
			long elapsedNanos,
			double pulseIntensity,
			double progress)
		{
			return new Snapshot(true, skill, elapsedNanos, pulseIntensity, progress);
		}

		public boolean isActive()
		{
			return active;
		}

		public Skill getSkill()
		{
			return skill;
		}

		public long getElapsedNanos()
		{
			return elapsedNanos;
		}

		public double getPulseIntensity()
		{
			return pulseIntensity;
		}

		public double getProgress()
		{
			return progress;
		}
	}
}
