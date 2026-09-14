package com.ashy0019.hapticscape.music;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class AudioCaptureApplicationTest
{
	@Test
	public void persistedIdentityIsCaseInsensitiveAndSurvivesMissingSession()
	{
		AudioCaptureApplication application = AudioCaptureApplication.fromPersisted(
			"COMMAND:C:\\Apps\\Spotify.exe",
			"Spotify"
		);

		assertEquals("command:c:\\apps\\spotify.exe", application.getId());
		assertEquals(
			application,
			new AudioCaptureApplication("command:C:\\APPS\\SPOTIFY.EXE", "Spotify")
		);
		AudioCaptureApplication unavailable = AudioCaptureApplication.unavailable(
			application.getId(),
			application.getDisplayName()
		);
		assertFalse(unavailable.isAvailable());
		assertEquals("Spotify (not currently playing)", unavailable.toString());
	}

	@Test
	public void blankPersistedIdentityMeansNoApplicationWasChosen()
	{
		assertNull(AudioCaptureApplication.fromPersisted(" ", "Ignored"));
	}
}
