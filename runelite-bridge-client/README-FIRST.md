# LumBridge

LumBridge is the fallback RuneLite launcher for HapticScape's Local Event Bridge.
It is intended for cases where the bridge is not installed through the RuneLite
Plugin Hub.

Run `LumBridge.exe` instead of the normal RuneLite launcher,
then start the standalone HapticScape desktop application. The bundled bridge
observes the same small allowlist of gameplay facts as the Plugin Hub version
and publishes them only over IPv4 loopback to `127.0.0.1:41713`.

The HapticScape desktop application itself is not embedded in RuneLite. The
RuneLite side contains only the Local Event Bridge plus its neutral protocol.

Do not enable a second Plugin Hub copy of Local Event Bridge inside this
LumBridge; running both would duplicate the same local event stream.

If the Local Event Bridge is available through the official RuneLite Plugin
Hub, prefer the official RuneLite client with that plugin installed; this
LumBridge package is then unnecessary.
