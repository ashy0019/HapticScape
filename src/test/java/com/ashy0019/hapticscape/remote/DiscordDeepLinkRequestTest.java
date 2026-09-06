package com.ashy0019.hapticscape.remote;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DiscordDeepLinkRequestTest
{
	private static final String CONTROLLER = "123456789012345678";
	private static final String REQUEST = "abcdefghijklmnop";
	private static final String TOKEN =
		"abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";

	@Test
	public void parsesOpaqueAcceptRequest()
	{
		DiscordDeepLinkRequest request = DiscordDeepLinkRequest.parse(
			"hapticscape://discord/accept?controller=" + CONTROLLER
				+ "&request=" + REQUEST + "&token=" + TOKEN
		);

		assertEquals(CONTROLLER, request.getControllerId());
		assertEquals(REQUEST, request.getRequestId());
		assertEquals(TOKEN, request.getAcceptToken());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsAuthorityBearingOrUnexpectedFields()
	{
		DiscordDeepLinkRequest.parse(
			"hapticscape://discord/accept?controller=" + CONTROLLER
				+ "&request=" + REQUEST + "&token=" + TOKEN
				+ "&invitation=HSP1.secret"
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsWebLinks()
	{
		DiscordDeepLinkRequest.parse(
			"https://example.test/accept?controller=" + CONTROLLER
				+ "&request=" + REQUEST + "&token=" + TOKEN
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsExplicitPorts()
	{
		DiscordDeepLinkRequest.parse(
			"hapticscape://discord:1234/accept?controller=" + CONTROLLER
				+ "&request=" + REQUEST + "&token=" + TOKEN
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTrailingQuerySeparators()
	{
		DiscordDeepLinkRequest.parse(
			"hapticscape://discord/accept?controller=" + CONTROLLER
				+ "&request=" + REQUEST + "&token=" + TOKEN + "&"
		);
	}
}
