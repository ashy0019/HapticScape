package com.ashy0019.hapticscape;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ashy0019.hapticscape.host.SourceMessageService;
import com.ashy0019.hapticscape.music.AudioCaptureSource;
import com.ashy0019.hapticscape.protocol.LocalhostGameplayEventTransport;
import com.ashy0019.hapticscape.protocol.SourceCapability;
import com.ashy0019.hapticscape.protocol.TransportWireCodec;
import com.ashy0019.hapticscape.remote.SettingsStore;
import com.ashy0019.hapticscape.remote.UnlockKeyProtector;
import com.ashy0019.hapticscape.storage.HapticScapeStoragePaths;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HapticScapeRuntimeTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void composesAndOwnsNeutralRuntimeGraph() throws Exception
    {
        MapSettingsStore store = new MapSettingsStore();
        SkillCatalog skills = new SkillCatalog(Arrays.asList(
            new SkillDescriptor("attack", "Attack"),
            new SkillDescriptor("cooking", "Cooking")
        ));
        HapticScapeSettingsSource settings = new SettingsBackedHapticScapeSettings(store, skills);
        Path dataDirectory = temporaryFolder.newFolder("runtime").toPath();
        Gson gson = new Gson();

        HapticScapeRuntime runtime = new HapticScapeRuntime(
            new HapticScapeRuntimeDependencies(
                new OkHttpClient(),
                gson,
                settings,
                store,
                skills,
                new HapticScapeStoragePaths(dataDirectory),
                (sound, gainDb) -> { },
                message -> { },
                new SourceMessageService()
                {
                    @Override
                    public void post(String message)
                    {
                    }

                    @Override
                    public void postColored(String message, int rgb)
                    {
                    }
                },
                HapticScapeRuntimeTest::unusedAudioCapture,
                new IdentityProtector(),
                new IdentityProtector(),
                0
            )
        );

        assertFalse(runtime.isStarted());
        runtime.start();
        try
        {
            assertTrue(runtime.isStarted());
            assertTrue(runtime.getGameplayTransportPort() > 0);
            assertNotNull(runtime.getIntifaceService());
            assertNotNull(runtime.getMusicSyncService());
            assertNotNull(runtime.getClickerService());
            assertNotNull(runtime.getRemoteSessionManager());
            assertNotNull(runtime.getDiscordPairingBridge());
            assertNotNull(runtime.getSettingsLockService());

            try (LocalhostGameplayEventTransport transport = new LocalhostGameplayEventTransport(
                "runelite",
                new TransportWireCodec(gson),
                EnumSet.allOf(SourceCapability.class),
                runtime.getGameplayTransportPort()))
            {
                // Constructor performs the real transport hello/ack handshake.
            }
        }
        finally
        {
            runtime.close();
        }
        assertFalse(runtime.isStarted());
    }

    private static AudioCaptureSource unusedAudioCapture()
    {
        return new AudioCaptureSource()
        {
            @Override
            public void start(Listener listener)
            {
                throw new AssertionError("Music capture must stay disabled in this test");
            }

            @Override
            public void close()
            {
            }
        };
    }

    private static final class MapSettingsStore implements SettingsStore
    {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key)
        {
            return values.get(key);
        }

        @Override
        public void set(String key, Object value)
        {
            values.put(key, String.valueOf(value));
        }
    }

    private static final class IdentityProtector implements UnlockKeyProtector
    {
        @Override
        public boolean isAvailable()
        {
            return true;
        }

        @Override
        public String getUnavailableMessage()
        {
            return "";
        }

        @Override
        public byte[] protect(byte[] plaintext)
        {
            return plaintext.clone();
        }

        @Override
        public byte[] unprotect(byte[] ciphertext)
        {
            return ciphertext.clone();
        }
    }
}
