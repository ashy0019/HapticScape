package com.ashy0019.hapticscape;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Level99CelebrationControllerTest
{
	private final AtomicLong nanoTime = new AtomicLong();
	private final Level99CelebrationController controller =
		new Level99CelebrationController(nanoTime::get);

	@Test
	public void startPublishesSkillAndFirstVisualBeat()
	{
		controller.start(new SkillDescriptor("agility", "Agility"));

		Level99CelebrationController.Snapshot snapshot = controller.snapshot();
		assertTrue(snapshot.isActive());
		assertEquals("agility", snapshot.getSkill().getId());
		assertEquals("Agility", snapshot.getSkill().getDisplayName());
		assertEquals(0.60, snapshot.getPulseIntensity(), 0.0001);
	}

	@Test
	public void visualPulseFollowsCeremonyGaps()
	{
		controller.start(new SkillDescriptor("agility", "Agility"));
		nanoTime.set(Duration.ofMillis(60).toNanos());

		assertEquals(0.0, controller.snapshot().getPulseIntensity(), 0.0001);
	}

	@Test
	public void ceremonyExpiresAfterItsCompleteDuration()
	{
		controller.start(new SkillDescriptor("agility", "Agility"));
		nanoTime.set(Level99Ceremony.totalDurationNanos());

		assertFalse(controller.snapshot().isActive());
	}

	@Test
	public void resetImmediatelyClearsCeremony()
	{
		controller.start(new SkillDescriptor("agility", "Agility"));
		controller.reset();

		assertFalse(controller.snapshot().isActive());
	}

	@Test
	public void startingAgainReplacesTheActiveSkillAndTimeline()
	{
		controller.start(new SkillDescriptor("agility", "Agility"));
		nanoTime.set(Duration.ofSeconds(2).toNanos());
		controller.start(new SkillDescriptor("cooking", "Cooking"));

		Level99CelebrationController.Snapshot snapshot = controller.snapshot();
		assertEquals("cooking", snapshot.getSkill().getId());
		assertEquals("Cooking", snapshot.getSkill().getDisplayName());
		assertEquals(0.60, snapshot.getPulseIntensity(), 0.0001);
	}
}
