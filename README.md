# HapticScape

A RuneLite plugin that translates supported Old School RuneScape events into
configurable haptic feedback through a user-operated Intiface server.

## Current features

- Connect to and disconnect from an Intiface server.
- Display connected devices and vibration support.
- Adjust the XP threshold, intensity, and pulse duration from the HapticScape panel.
- Enable or disable XP feedback for individual skills.
- Send a configurable test pulse.
- Trigger a pulse when one XP gain meets the configured threshold.
- Stop all devices immediately.
- Detect connection loss without blocking RuneLite.
- Communicate over the Intiface WebSocket protocol using RuneLite's existing
  HTTP and JSON libraries; no additional runtime dependencies are bundled.

## Design

- RuneLite callbacks detect game events and never perform device I/O.
- A dedicated service owns the Intiface connection and its worker thread.
- The gateway requests Intiface protocol version 3 to preserve compatibility
  with the previously tested implementation.
- Connection failures are reported as state and never escape into RuneLite's event bus.
- Swing components are only touched on Swing's event-dispatch thread.

## Privacy and networking

The default server is `ws://localhost:12345`, so device commands remain on the
user's computer. HapticScape sends protocol handshakes and haptic device
commands to the server URI configured by the user. It does not send RuneScape
account names, chat, credentials, or information about other players.

Using a non-local server reveals the user's IP address and command traffic to
that independently operated server.

## Private Windows bundle

HapticScape can be packaged as a portable Windows application for private
testing. This is an unofficial RuneLite build and is not endorsed by RuneLite
or Jagex.

Prerequisites:

- Windows 10 or newer.
- The official RuneLite launcher installed. HapticScape reuses RuneLite's
  bundled Java runtime.
- Intiface installed separately.

From PowerShell in the repository root, run:

```powershell
.\package-windows.ps1
```

The script runs the tests, builds the client, creates a small native Windows
launcher, and produces
`build/distribution/HapticScape-Windows-<architecture>-1.0.0.zip`.
Distribute the ZIP rather than the executable by itself. Never include or
share `.runelite/credentials.properties`.
