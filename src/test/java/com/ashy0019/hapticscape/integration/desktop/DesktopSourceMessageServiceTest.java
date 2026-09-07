package com.ashy0019.hapticscape.integration.desktop;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DesktopSourceMessageServiceTest
{
	@Test
	public void publishesPlainAndColoredMessagesToDesktopListener()
	{
		DesktopSourceMessageService service = new DesktopSourceMessageService();
		AtomicReference<DesktopSourceMessageService.Message> latest = new AtomicReference<>();
		service.setListener(latest::set);

		service.post("hello");
		assertEquals("hello", latest.get().getText());
		assertNull(latest.get().getRgb());

		service.postColored("mastery", 0xFFAE00);
		assertEquals("mastery", latest.get().getText());
		assertEquals(Integer.valueOf(0xFFAE00), latest.get().getRgb());
	}
}
