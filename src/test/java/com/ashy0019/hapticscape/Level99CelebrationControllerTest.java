package com.ashy0019.hapticscape;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Skill;
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
		controller.start(Skill.AGILITY);

		Level99CelebrationController.Snapshot snapshot = controller.snapshot();
		assertTrue(snapshot.isActive());
		assertEquals(Skill.AGILITY, snapshot.getSkill());
		assertEquals(0.60, snapshot.getPulseIntensity(), 0.0001);
	}

	@Test
	public void visualPulseFollowsCeremonyGaps()
	{
		controller.start(Skill.AGILITY);
		nanoTime.set(Duration.ofMillis(60).toNanos());

		assertEquals(0.0, controller.snapshot().getPulseIntensity(), 0.0001);
	}

	@Test
	public void ceremonyExpiresAfterItsCompleteDuration()
	{
		controller.start(Skill.AGILITY);
		nanoTime.set(Level99Ceremony.totalDurationNanos());

		assertFalse(controller.snapshot().isActive());
	}

	@Test
	public void resetImmediatelyClearsCeremony()
	{
		controller.start(Skill.AGILITY);
		controller.reset();

		assertFalse(controller.snapshot().isActive());
	}

	@Test
	public void startingAgainReplacesTheActiveSkillAndTimeline()
	{
		controller.start(Skill.AGILITY);
		nanoTime.set(Duration.ofSeconds(2).toNanos());
		controller.start(Skill.COOKING);

		Level99CelebrationController.Snapshot snapshot = controller.snapshot();
		assertEquals(Skill.COOKING, snapshot.getSkill());
		assertEquals(0.60, snapshot.getPulseIntensity(), 0.0001);
	}
}
