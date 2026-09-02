# HapticScape

HapticScape turns Old School RuneScape events and Windows audio into
configurable haptic feedback. It connects RuneLite to devices managed by
[Intiface Central](https://intiface.com/) and gives you control over what
triggers feedback, how it feels, and which devices receive it.

HapticScape is an unofficial RuneLite integration. It is not endorsed by
RuneLite, Jagex, or the Intiface project.

## Features

### XP and skill feedback

- Trigger a haptic response when one XP gain reaches your chosen threshold.
- Enable or disable feedback for each of the 24 skills.
- Use global intensity, duration, and pattern settings for a simple setup.
- Give individual skills their own threshold, intensity, duration, and pattern.
- Configure separate feedback for ordinary level-ups.
- Assign a dedicated pattern to level milestones from 10 through 90.
- Preview global XP, level-up, and per-skill feedback from the side panel.

### Semantic alerts

HapticScape can distinguish useful events instead of treating every RuneLite
notification the same way:

- Direct messages
- Trade requests
- Low hitpoints
- Low prayer
- Valuable drops
- Full inventory
- Poison or venom
- Special attack ready
- Player death

Each alert can be disabled, inherit the Generic profile, or use its own
pattern, intensity, and duration. Relevant categories also provide their own
trigger settings, including hitpoints, prayer, loot value, and special-attack
energy thresholds.

The Generic profile remains the fallback for ordinary RuneLite notifications
that do not match a more specific category. When both a specific event and a
generic notification describe the same occurrence, HapticScape prioritizes
the specific profile to avoid duplicate feedback.

### Haptic controls and built-in patterns

- Adjust intensity from 0% to 100%.
- Set built-in pattern duration from 50 ms to 10 seconds.
- Choose Single, Double, Triple, Ascending, or Descending feedback.
- Test profiles without waiting for the corresponding in-game event.
- Stop every connected device immediately with **Stop now**.

### Custom Pattern Forge

Pattern Forge lets you create reusable feedback without writing code:

- Draw an intensity curve directly with the mouse.
- Set one beat to any length from 50 ms to 10 seconds.
- Repeat the beat from 1 to 72 times.
- Undo edits, clear the canvas, preview the result, and save it.
- Add, rename, and delete up to 100 custom patterns.
- Assign custom patterns anywhere a built-in pattern can be selected.

A custom pattern keeps its own curve, beat length, and repetition count. Those
values are not rescaled by a skill or alert profile's intensity and duration
controls. Renaming a custom pattern preserves its existing assignments, while
deleting one safely returns those assignments to Single pulse.

### Music sync on Windows

Music sync converts the current Windows audio output into continuous haptic
feedback using a local FFT-based analyzer.

- Choose Smooth, Rhythmic, or Punchy response styles.
- Adjust sensitivity and independent minimum and maximum output levels.
- Watch the mapped haptic output on a live meter.
- Let actual system audio volume scale the response automatically.
- Respect the Windows master-volume slider and mute state.
- Allow XP feedback, alerts, and custom patterns to interrupt music sync and
  resume it automatically afterward.
- Disable music sync immediately when **Stop now** is pressed.

Because Music sync listens to the Windows output device, audio from other
applications using that device can influence the response too.

### Intiface connection and device controls

- Connect to or disconnect from Intiface without restarting RuneLite.
- Display discovered devices and whether they support vibration.
- Send a configurable test pattern.
- Recover cleanly when Intiface stops or the connection is lost.
- Reconnect after restarting Intiface without relaunching the client.

### Windows client updates

The Windows launcher can check stable GitHub Releases before starting the
client. Update installation and update notifications are separate choices, so
you can choose prompted updates, silent automatic updates, notifications only,
or no startup checks at all.

- Check for an update manually at any time.
- Install now, postpone the update, or skip one particular version.
- Verify the downloaded ZIP against its published SHA-256 checksum.
- Stage and validate the new bundle before replacing the installed version.
- Restore the previous bundle if installation cannot be completed.
- Launch the currently installed client when GitHub is unavailable.

Version 1.6.0 is the first updater-enabled bundle and must be installed
manually. Compatible releases published after it can be installed by the
launcher.

## Panel guide

The Feedback section contains the global XP controls plus level-up and
milestone settings. The tabs organize the more detailed features:

| Tab | What it controls |
|---|---|
| **Skills** | Enable all skills, disable all skills, or toggle them individually. |
| **XP** | Select a skill and inherit or override the global XP settings. |
| **Alerts** | Configure the Generic fallback and individual gameplay alert profiles. |
| **Forge** | Draw, preview, save, rename, and manage custom patterns. |
| **Music** | Enable Windows audio sync and tune its response. |

The bottom of the panel contains the Intiface connection controls, global test
button, emergency stop, update controls, and connected-device list.

## Requirements

- RuneLite and a supported Old School RuneScape account.
- [Intiface Central](https://intiface.com/) installed and running.
- A device supported by Intiface.
- Bluetooth, USB, serial, or another connection method required by that
  device.

See the official
[Intiface Central quickstart](https://intiface.com/docs/intiface-central/quickstart/)
for device-specific setup.

## Quick start

1. Open Intiface Central.
2. Start its engine/server.
3. Scan for and connect your device in Intiface.
4. Confirm the device responds to Intiface's test controls.
5. Launch the HapticScape RuneLite build.
6. Open the HapticScape side panel and select **Connect**.
7. Keep `ws://localhost:12345` as the server address unless Intiface runs on
   another computer or port.
8. Select **Test pattern** and choose a comfortable intensity and duration.

Different devices have different minimum usable intensities. If yours is
silent at low percentages, raise the intensity—or Music sync's Minimum
setting—above the device's physical threshold.

## Running from source

HapticScape targets Java 11 and includes the Gradle wrapper, so a separate
system-wide Gradle installation is not required.

### Prerequisites

- Git
- A Java 11 JDK
- Intiface Central

Clone the repository and enter it:

```powershell
git clone https://github.com/ashy0019/HapticScape.git
cd HapticScape
```

Run the test suite:

```powershell
.\gradlew.bat clean test
```

Launch the development client:

```powershell
.\gradlew.bat run
```

On macOS or Linux, use `./gradlew` instead of `.\gradlew.bat`.

### Jagex Accounts

Developers using a Jagex Account may need to create local RuneLite credentials
by following RuneLite's
[Using Jagex Accounts development guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

Never commit, upload, package, or share
`.runelite/credentials.properties`. It contains credentials for the account
that generated it.

## Building the client JAR

Run:

```powershell
.\gradlew.bat clean test shadowJar
```

The runnable client is written to:

```text
build\libs\hapticscape-client.jar
```

Launch it manually with Java 11:

```powershell
java -ea -jar .\build\libs\hapticscape-client.jar --developer-mode --debug
```

This is an unofficial development client containing HapticScape, not an
official RuneLite distribution.

## Building the Windows bundle

The Windows packager creates a small `HapticScape.exe` launcher and bundles it
with the client JAR. It does not package Intiface or account credentials.

### Windows build prerequisites

- Windows 10 or newer
- Java 11 available through `JAVA_HOME` or `PATH`
- The official RuneLite launcher installed
- Windows .NET Framework 4.x enabled
- Intiface Central installed separately for testing

From PowerShell in the repository root:

```powershell
.\package-windows.ps1 -Version 1.6.0
```

The script runs the Java and native updater tests, builds the client JAR,
compiles the Windows launcher and update helper, gathers required licenses and
release metadata, then creates a distributable ZIP and checksum in
`build\distribution`.

For a 64-bit version 1.6.0 build, the output is:

```text
build\distribution\HapticScape-Windows-x64-1.6.0.zip
build\distribution\HapticScape-Windows-x64-1.6.0.zip.sha256
```

Test the unpacked launcher before publishing or sharing the ZIP:

```text
build\windows-package\HapticScape\HapticScape.exe
```

Distribute the complete ZIP, not `HapticScape.exe` by itself. The launcher
requires the accompanying `app` directory. Windows may display a SmartScreen
warning because private builds are not code-signed.

## Publishing an updater-compatible release

1. Run `.\package-windows.ps1 -Version X.Y.Z`.
2. Tag that exact commit as `vX.Y.Z`.
3. Create a GitHub Release from the tag.
4. Upload both the generated ZIP and its matching `.zip.sha256` file.

The asset names, Git tag, embedded version, and architecture must agree.
Drafts and prereleases are not offered through the stable update channel.
Update preferences are stored in
`.runelite\hapticscape\updater-settings.json`, outside the replaceable client
bundle.

## Privacy and networking

The default Intiface address, `ws://localhost:12345`, keeps the connection on
the same computer. HapticScape sends only the protocol handshake and device
commands needed to play your configured feedback. It does not send account
names, chat messages, passwords, other-player information, or RuneLite
notification text to Intiface.

Music sync analyzes a small rolling window of the current Windows output mix
in memory. Audio is never recorded, saved, or transmitted by HapticScape.

When update checks are enabled, the Windows launcher requests public release
metadata from GitHub. It does not add account, character, chat, device, or
gameplay data to that request. Disable both automatic updates and update
notifications to prevent startup update requests. Selecting **Check now**
still performs a user-requested GitHub check.

If you configure a non-local Intiface address, that server's operator can
receive your IP address and haptic command traffic. Only connect to remote
servers you trust.

## Troubleshooting

### HapticScape stays on Connecting

- Confirm Intiface Central is open and its engine/server is running.
- Confirm the configured server address and port match Intiface.
- Use `ws://localhost:12345` when both applications run on the same computer.
- Disconnect and reconnect after restarting Intiface.

### The device does not appear

- Scan for the device inside Intiface Central first.
- Confirm it works with Intiface's own test controls.
- Close manufacturer applications or other software that may control it.
- Check Intiface's documentation for its Bluetooth, USB, or serial needs.

### The device appears but does not respond

- Confirm Intiface reports vibration or scalar support for the device.
- Increase intensity; some devices ignore low values.
- Select **Stop now**, reconnect, and send another test pattern.
- Test a built-in Single pulse before troubleshooting a custom curve.

### The Windows launcher cannot find Java

Install the official RuneLite launcher first. HapticScape looks for RuneLite's
bundled Java runtime, then checks `JAVA_HOME` and `PATH`.

## License and acknowledgements

HapticScape is distributed under the terms in [LICENSE](LICENSE). Windows
bundles include applicable third-party notices and RuneLite's license in their
`app\licenses` directory.

RuneLite is a third-party client for Old School RuneScape. Intiface Central is
an independent open-source project built on the Buttplug framework. All names
and trademarks belong to their respective owners.

Bug reports and feature requests are welcome through
[GitHub Issues](https://github.com/ashy0019/HapticScape/issues).
