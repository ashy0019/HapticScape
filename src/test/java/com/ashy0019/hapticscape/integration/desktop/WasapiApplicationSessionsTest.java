package com.ashy0019.hapticscape.integration.desktop;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WasapiApplicationSessionsTest
{
	@Test
	public void executableCommandBecomesReadableMixerFallbackName()
	{
		assertEquals(
			"Spotify",
			WasapiApplicationSessions.displayNameFromCommand(
				"C:\\Users\\Example\\AppData\\Roaming\\Spotify\\Spotify.exe"
			)
		);
	}

	@Test
	public void taskbarWindowNamesHostedJavaApplications()
	{
		assertEquals(
			"RuneLite",
			WasapiApplicationSessions.preferredDisplayName(
				"",
				"RuneLite",
				"C:\\Program Files\\Java\\bin\\javaw.exe"
			)
		);
		assertEquals(
			"RuneLite",
			WasapiApplicationSessions.preferredDisplayName(
				"Javaw",
				"RuneLite",
				"C:\\Program Files\\Java\\bin\\javaw.exe"
			)
		);
	}

	@Test
	public void specificAudioSessionNameRemainsPreferredForNormalApplications()
	{
		assertEquals(
			"Spotify",
			WasapiApplicationSessions.preferredDisplayName(
				"Spotify",
				"Song title - Spotify",
				"C:\\Users\\Example\\AppData\\Roaming\\Spotify\\Spotify.exe"
			)
		);
	}

	@Test
	public void taskbarWindowIsGenericFallbackWhenSessionHasNoName()
	{
		assertEquals(
			"Game client",
			WasapiApplicationSessions.preferredDisplayName(
				"",
				"Game client",
				"C:\\Games\\client.exe"
			)
		);
	}

	@Test
	public void matchingTaskbarTitleSelectsTheVisibleApplicationProcess()
	{
		assertEquals(100, WasapiApplicationSessions.processTitleScore("RuneLite", "RuneLite"));
		assertEquals(
			75,
			WasapiApplicationSessions.processTitleScore("RuneLite - Ashlyn", "RuneLite")
		);
		assertEquals(10, WasapiApplicationSessions.processTitleScore("Other window", "RuneLite"));
		assertEquals(0, WasapiApplicationSessions.processTitleScore("", "RuneLite"));
	}
}
