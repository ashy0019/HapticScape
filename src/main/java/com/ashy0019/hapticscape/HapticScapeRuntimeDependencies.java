package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.audio.SoundPlayer;
import com.ashy0019.hapticscape.host.DesktopNotificationService;
import com.ashy0019.hapticscape.host.SourceMessageService;
import com.ashy0019.hapticscape.music.AudioCaptureSource;
import com.ashy0019.hapticscape.music.AudioCaptureEndpoint;
import com.ashy0019.hapticscape.music.AudioCaptureEndpointCatalog;
import com.ashy0019.hapticscape.music.AudioCaptureSourceFactory;
import com.ashy0019.hapticscape.music.AudioCaptureApplication;
import com.ashy0019.hapticscape.music.AudioCaptureApplicationCatalog;
import com.ashy0019.hapticscape.music.AudioCaptureMode;
import com.ashy0019.hapticscape.remote.SettingsStore;
import com.ashy0019.hapticscape.remote.UnlockKeyProtector;
import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import com.google.gson.Gson;
import java.util.Objects;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;

/**
 * Host-neutral dependencies required to compose one HapticScape runtime.
 *
 * <p>Different hosts provide their own implementations of these boundaries,
 * while the runtime graph itself remains identical.</p>
 */
public final class HapticScapeRuntimeDependencies
{
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final HapticScapeSettingsSource settings;
    private final SettingsStore settingsStore;
    private final SkillCatalog skillCatalog;
    private final HapticScapeStoragePaths storagePaths;
    private final SoundPlayer soundPlayer;
    private final DesktopNotificationService desktopNotifications;
    private final SourceMessageService sourceMessages;
    private final AudioCaptureSourceFactory audioCaptureSourceFactory;
    private final AudioCaptureEndpointCatalog audioCaptureEndpointCatalog;
    private final AudioCaptureEndpoint initialAudioCaptureEndpoint;
	private final AudioCaptureApplicationCatalog audioCaptureApplicationCatalog;
	private final AudioCaptureMode initialAudioCaptureMode;
	private final AudioCaptureApplication initialAudioCaptureApplication;
    private final UnlockKeyProtector savedUnlockKeyProtector;
    private final UnlockKeyProtector discordCredentialProtector;
    private final int gameplayPort;

    public HapticScapeRuntimeDependencies(
        OkHttpClient httpClient,
        Gson gson,
        HapticScapeSettingsSource settings,
        SettingsStore settingsStore,
        SkillCatalog skillCatalog,
        HapticScapeStoragePaths storagePaths,
        SoundPlayer soundPlayer,
        DesktopNotificationService desktopNotifications,
        SourceMessageService sourceMessages,
        Supplier<AudioCaptureSource> audioCaptureSourceFactory,
        UnlockKeyProtector savedUnlockKeyProtector,
        UnlockKeyProtector discordCredentialProtector,
        int gameplayPort)
    {
        this(
            httpClient,
            gson,
            settings,
            settingsStore,
            skillCatalog,
            storagePaths,
            soundPlayer,
            desktopNotifications,
            sourceMessages,
            ignored -> audioCaptureSourceFactory.get(),
            AudioCaptureEndpointCatalog.systemDefaultOnly(),
            AudioCaptureEndpoint.systemDefault(),
			AudioCaptureApplicationCatalog.empty(),
			AudioCaptureMode.OUTPUT,
			null,
            savedUnlockKeyProtector,
            discordCredentialProtector,
            gameplayPort
        );
    }

    public HapticScapeRuntimeDependencies(
        OkHttpClient httpClient,
        Gson gson,
        HapticScapeSettingsSource settings,
        SettingsStore settingsStore,
        SkillCatalog skillCatalog,
        HapticScapeStoragePaths storagePaths,
        SoundPlayer soundPlayer,
        DesktopNotificationService desktopNotifications,
        SourceMessageService sourceMessages,
        AudioCaptureSourceFactory audioCaptureSourceFactory,
        AudioCaptureEndpointCatalog audioCaptureEndpointCatalog,
        AudioCaptureEndpoint initialAudioCaptureEndpoint,
        UnlockKeyProtector savedUnlockKeyProtector,
        UnlockKeyProtector discordCredentialProtector,
        int gameplayPort)
	{
		this(
			httpClient, gson, settings, settingsStore, skillCatalog, storagePaths,
			soundPlayer, desktopNotifications, sourceMessages,
			audioCaptureSourceFactory, audioCaptureEndpointCatalog,
			initialAudioCaptureEndpoint, AudioCaptureApplicationCatalog.empty(),
			AudioCaptureMode.OUTPUT, null, savedUnlockKeyProtector,
			discordCredentialProtector, gameplayPort
		);
	}

	public HapticScapeRuntimeDependencies(
		OkHttpClient httpClient,
		Gson gson,
		HapticScapeSettingsSource settings,
		SettingsStore settingsStore,
		SkillCatalog skillCatalog,
		HapticScapeStoragePaths storagePaths,
		SoundPlayer soundPlayer,
		DesktopNotificationService desktopNotifications,
		SourceMessageService sourceMessages,
		AudioCaptureSourceFactory audioCaptureSourceFactory,
		AudioCaptureEndpointCatalog audioCaptureEndpointCatalog,
		AudioCaptureEndpoint initialAudioCaptureEndpoint,
		AudioCaptureApplicationCatalog audioCaptureApplicationCatalog,
		AudioCaptureMode initialAudioCaptureMode,
		AudioCaptureApplication initialAudioCaptureApplication,
		UnlockKeyProtector savedUnlockKeyProtector,
		UnlockKeyProtector discordCredentialProtector,
		int gameplayPort)
    {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog");
        this.storagePaths = Objects.requireNonNull(storagePaths, "storagePaths");
        this.soundPlayer = Objects.requireNonNull(soundPlayer, "soundPlayer");
        this.desktopNotifications = Objects.requireNonNull(
            desktopNotifications,
            "desktopNotifications"
        );
        this.sourceMessages = Objects.requireNonNull(sourceMessages, "sourceMessages");
        this.audioCaptureSourceFactory = Objects.requireNonNull(
            audioCaptureSourceFactory,
            "audioCaptureSourceFactory"
        );
        this.audioCaptureEndpointCatalog = Objects.requireNonNull(
            audioCaptureEndpointCatalog,
            "audioCaptureEndpointCatalog"
        );
        this.initialAudioCaptureEndpoint = Objects.requireNonNull(
            initialAudioCaptureEndpoint,
            "initialAudioCaptureEndpoint"
        );
		this.audioCaptureApplicationCatalog = Objects.requireNonNull(
			audioCaptureApplicationCatalog,
			"audioCaptureApplicationCatalog"
		);
		this.initialAudioCaptureMode = Objects.requireNonNull(
			initialAudioCaptureMode,
			"initialAudioCaptureMode"
		);
		this.initialAudioCaptureApplication = initialAudioCaptureApplication;
        this.savedUnlockKeyProtector = Objects.requireNonNull(
            savedUnlockKeyProtector,
            "savedUnlockKeyProtector"
        );
        this.discordCredentialProtector = Objects.requireNonNull(
            discordCredentialProtector,
            "discordCredentialProtector"
        );
        if (gameplayPort < 0 || gameplayPort > 65_535)
        {
            throw new IllegalArgumentException("gameplayPort must be between 0 and 65535");
        }
        this.gameplayPort = gameplayPort;
    }

    OkHttpClient getHttpClient()
    {
        return httpClient;
    }

    Gson getGson()
    {
        return gson;
    }

    HapticScapeSettingsSource getSettings()
    {
        return settings;
    }

    SettingsStore getSettingsStore()
    {
        return settingsStore;
    }

    SkillCatalog getSkillCatalog()
    {
        return skillCatalog;
    }

    HapticScapeStoragePaths getStoragePaths()
    {
        return storagePaths;
    }

    SoundPlayer getSoundPlayer()
    {
        return soundPlayer;
    }

    DesktopNotificationService getDesktopNotifications()
    {
        return desktopNotifications;
    }

    SourceMessageService getSourceMessages()
    {
        return sourceMessages;
    }

    AudioCaptureSourceFactory getAudioCaptureSourceFactory()
    {
        return audioCaptureSourceFactory;
    }

    AudioCaptureEndpointCatalog getAudioCaptureEndpointCatalog()
    {
        return audioCaptureEndpointCatalog;
    }

    AudioCaptureEndpoint getInitialAudioCaptureEndpoint()
    {
        return initialAudioCaptureEndpoint;
    }

	AudioCaptureApplicationCatalog getAudioCaptureApplicationCatalog()
	{
		return audioCaptureApplicationCatalog;
	}

	AudioCaptureMode getInitialAudioCaptureMode()
	{
		return initialAudioCaptureMode;
	}

	AudioCaptureApplication getInitialAudioCaptureApplication()
	{
		return initialAudioCaptureApplication;
	}

    UnlockKeyProtector getSavedUnlockKeyProtector()
    {
        return savedUnlockKeyProtector;
    }

    UnlockKeyProtector getDiscordCredentialProtector()
    {
        return discordCredentialProtector;
    }

    int getGameplayPort()
    {
        return gameplayPort;
    }
}
