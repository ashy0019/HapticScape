package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.audio.SoundPlayer;
import com.ashy0019.hapticscape.host.DesktopNotificationService;
import com.ashy0019.hapticscape.host.SourceMessageService;
import com.ashy0019.hapticscape.music.AudioCaptureSource;
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
 * <p>RuneLite and the future standalone desktop process provide different
 * implementations of these boundaries, while the runtime graph itself remains
 * identical.</p>
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
    private final Supplier<AudioCaptureSource> audioCaptureSourceFactory;
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

    Supplier<AudioCaptureSource> getAudioCaptureSourceFactory()
    {
        return audioCaptureSourceFactory;
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
