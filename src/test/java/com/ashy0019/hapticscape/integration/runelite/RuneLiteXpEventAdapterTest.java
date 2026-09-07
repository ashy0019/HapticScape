package com.ashy0019.hapticscape.integration.runelite;

import com.ashy0019.hapticscape.event.XpEvent;
import com.ashy0019.hapticscape.event.XpEventTracker;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteXpEventAdapterTest
{
	private final RuneLiteXpEventAdapter adapter = new RuneLiteXpEventAdapter();
	private final XpEventTracker tracker = new XpEventTracker();

	@Test
	public void mapsRuneLiteSkillToStableNeutralIdentifier()
	{
		adapter.seed(tracker, Skill.COOKING, 1_000);
		XpEvent event = adapter.update(tracker, Skill.COOKING, 1_050);

		assertEquals("runelite", event.getSource());
		assertEquals("cooking", event.getSkillId());
		assertEquals(50, event.getGainedXp());
	}

	@Test
	public void virtualLevelsAboveNinetyNineRemainCappedAtNinetyNine()
	{
		adapter.seed(tracker, Skill.AGILITY, Experience.getXpForLevel(99));
		XpEvent event = adapter.update(
			tracker,
			Skill.AGILITY,
			Experience.getXpForLevel(100)
		);

		assertFalse(event.isLevelUp());
		assertEquals(99, event.getPreviousLevel());
		assertEquals(99, event.getCurrentLevel());
	}

	@Test
	public void levelNinetyNineCrossingOnlyOccursOnce()
	{
		adapter.seed(tracker, Skill.AGILITY, Experience.getXpForLevel(98));

		XpEvent levelNinetyNine = adapter.update(
			tracker,
			Skill.AGILITY,
			Experience.getXpForLevel(99)
		);
		XpEvent laterXp = adapter.update(
			tracker,
			Skill.AGILITY,
			Experience.getXpForLevel(99) + 1_000
		);

		assertTrue(levelNinetyNine.crossedLevel(99));
		assertFalse(laterXp.crossedLevel(99));
	}
}
