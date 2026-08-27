# HapticScape

A RuneLite plugin that translates supported Old School RuneScape events into
configurable haptic feedback through a user-operated Intiface server.

## Current features

- Connect to and disconnect from an Intiface server
- Display connected devices
- Send a configurable test pulse
- Trigger a pulse when XP gain meets the configured threshold
- Stop all devices immediately

## Privacy and networking

The default server is `ws://localhost:12345`, so device commands remain on the
user's computer. HapticScape sends protocol handshakes and haptic device
commands to the server URI configured by the user. It does not send RuneScape
account names, chat, credentials, or information about other players.

Using a non-local server reveals the user's IP address and command traffic to
that independently operated server.
