package com.ashy0019.hapticscape;

import com.ashy0019.hapticscape.remote.RemotePermissionsSource;
import com.ashy0019.hapticscape.remote.RemoteSettingsSource;

/**
 * Full source-neutral settings view used by the HapticScape runtime and UI.
 *
 * <p>RuneLite may implement this through its config system; a standalone host
 * can provide the same values from a file-backed or in-memory settings store.</p>
 */
public interface HapticScapeSettingsSource extends RemoteSettingsSource, RemotePermissionsSource
{
    String DEFAULT_REMOTE_RELAY_URL =
        "wss://hapticscape-remote-relay.hapticscape.workers.dev/relay";

    String intifaceServer();

    String remoteRelayUrl();

    static String resolveRemoteRelayUrl(String configuredValue)
    {
        if (configuredValue == null || configuredValue.trim().isEmpty())
        {
            return DEFAULT_REMOTE_RELAY_URL;
        }
        return configuredValue.trim();
    }
}
