package com.ashy0019.hapticscape.ui;

import com.ashy0019.hapticscape.Level99CelebrationController;
import com.ashy0019.hapticscape.SkillDescriptor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Level99CelebrationRendererTest
{
	@Test
	public void inactiveCelebrationDoesNotRender()
	{
		Level99CelebrationController controller = new Level99CelebrationController();
		Level99CelebrationRenderer renderer = new Level99CelebrationRenderer(controller);
		Graphics2D graphics = graphics();
		try
		{
			assertNull(renderer.render(graphics));
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void activeCelebrationRendersExpectedBounds()
	{
		Level99CelebrationController controller = new Level99CelebrationController();
		controller.start(new SkillDescriptor("cooking", "Cooking"));
		Level99CelebrationRenderer renderer = new Level99CelebrationRenderer(controller);
		Graphics2D graphics = graphics();
		try
		{
			assertEquals(new Dimension(520, 250), renderer.render(graphics));
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static Graphics2D graphics()
	{
		return new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
	}
}
