# HapticScape

HapticScape turns Old School RuneScape events into configurable haptic feedback.
It connects RuneLite to devices managed by
[Intiface Central](https://intiface.com/) and gives each player control over
what triggers feedback, how strong it feels, how long it lasts, and what
pattern it follows.

HapticScape is an unofficial RuneLite integration. It is not endorsed by
RuneLite, Jagex, or the Intiface project.

## Features
<table>
<tr>
<td rowspan="3" valign="top"><a href="https://github.com/user-attachments/assets/afccc02a-d017-4780-a7a4-6b12f6f4edb7"><img src="https://github.com/user-attachments/assets/afccc02a-d017-4780-a7a4-6b12f6f4edb7" width="180" alt="HapticScape main panel"></a></td>
<td valign="top"><a href="https://github.com/user-attachments/assets/0ebc2624-0bf8-4cbe-9881-74faf7187de9"><img src="https://github.com/user-attachments/assets/0ebc2624-0bf8-4cbe-9881-74faf7187de9" width="180" alt="HapticScape skill profiles"></a></td>
</tr>
<tr>
<td valign="top"><a href="https://github.com/user-attachments/assets/ba565485-ab23-4a56-9c72-c26366a8a78a"><img src="https://github.com/user-attachments/assets/ba565485-ab23-4a56-9c72-c26366a8a78a" width="180" alt="HapticScape alerts"></a></td>
</tr>
<tr>
<td valign="top"><a href="https://github.com/user-attachments/assets/6044a42d-67f2-4d3c-a23a-ea7abd155594"><img src="https://github.com/user-attachments/assets/6044a42d-67f2-4d3c-a23a-ea7abd155594" width="180" alt="HapticScape custom pattern editor"></a></td>
</tr>
</table>

### RuneScape feedback

- Trigger feedback when a single XP gain reaches a configurable threshold.
- Enable or disable XP feedback for each RuneScape skill.
- Give individual skills their own XP threshold, intensity, duration, and
  pattern.
- Use separate feedback for ordinary level-ups.
- Choose a dedicated pattern for level milestones from 10 through 90 as well as a distinct level 99 pattern.
- Trigger separate feedback for RuneLite notifications.

### Haptic controls

- Adjust intensity from 0% to 100%.
- Set built-in pattern duration from 50 ms to 10 seconds.
- Choose from Single, Double, Triple, and Ascending built-in patterns.
- Preview global XP, level-up, skill-profile, and notification feedback without
  waiting for an in-game event.
- Stop every connected device immediately with **Stop now**.

### Custom Pattern Forge

- Draw an intensity curve directly with the mouse.
- Set the length of one drawn beat from 50 ms to 10 seconds.
- Repeat that beat anywhere from 1 to 72 times.
- Reuse the saved custom timing for XP, level-ups, skill profiles, milestones,
  and notifications while retaining each trigger's configured intensity.
- Undo edits, clear the canvas, and save changes.
- Add, rename, and delete custom patterns.
- Keep up to 100 custom patterns at once.
- Assign custom patterns anywhere a built-in pattern can be selected.
- Preserve existing pattern assignments when a custom pattern is renamed.
- Safely return deleted assignments to Single pulse.

### Connection experience

- Connect to and disconnect from Intiface without restarting RuneLite.
- Display discovered devices and their vibration support.
- Recover cleanly after Intiface is stopped or loses the connection.

## The HapticScape panel

The main Feedback section controls the default XP response, ordinary level-up
feedback, and milestone feedback. Four tabs provide the remaining controls:

| Tab | Purpose |
|---|---|
| **Skills** | Enable all skills, disable all skills, or toggle skills individually. |
| **Profiles** | Override the global XP settings for one selected skill. |
| **Alerts** | Configure feedback for RuneLite notifications. |
| **Custom** | Create, preview, name, save, and manage custom patterns. |

The bottom of the panel contains the connection controls, global test button,
emergency stop, and the list of devices reported by Intiface.

## Requirements

- A supported Old School RuneScape account and RuneLite setup.
- [Intiface Central](https://intiface.com/) installed and running.
- Supported Intiface device.
- Bluetooth, USB, serial, or another connection method supported by the device
  and Intiface.

For device-specific setup, use the official
[Intiface Central quickstart](https://intiface.com/docs/intiface-central/quickstart/).

## Connecting for the first time

1. Install and open Intiface Central.
2. Configure the appropriate device manager in Intiface.
3. Start the Intiface engine/server.
4. Scan for and connect your device in Intiface.
5. Confirm that the device responds to Intiface's own test controls.
6. Launch the HapticScape RuneLite build.
7. Open the HapticScape side panel and select **Connect**.
8. Leave the server address at `ws://localhost:12345` unless Intiface is
   running elsewhere.
9. Select **Test pattern** and adjust the intensity and duration to a
   comfortable level.

Different devices have different minimum usable intensities. If a device is
silent at low percentages but begins responding above a particular value,
raise HapticScape's intensity above that device's physical threshold.

## Running from source

HapticScape targets Java 11 and includes the Gradle wrapper, so a separate
system-wide Gradle installation is not required.

### Prerequisites

- Git
- A Java 11 JDK
- Intiface Central

Clone the repository:

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

Developers using a Jagex Account may need to create their own local RuneLite
credentials by following RuneLite's
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

It can be launched manually with Java 11:

```powershell
java -ea -jar .\build\libs\hapticscape-client.jar --developer-mode --debug
```

This is an unofficial development client containing HapticScape; it is not an
official RuneLite distribution.

## Building the private Windows bundle

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
.\package-windows.ps1
```

To specify a release version:

```powershell
.\package-windows.ps1 -Version 1.0.0
```

The script:

1. Runs the tests.
2. Builds `hapticscape-client.jar`.
3. Compiles the native Windows launcher.
4. Adds the required license files and friend setup guide.
5. Creates a distributable ZIP in `build\distribution`.

For version 1.0.0 on a 64-bit Windows computer, the output is:

```text
build\distribution\HapticScape-Windows-x64-1.0.0.zip
```

Test the unpacked launcher before publishing or sharing the ZIP:

```text
build\windows-package\HapticScape\HapticScape.exe
```

Distribute the complete ZIP, not `HapticScape.exe` by itself. The launcher
expects the accompanying `app` directory to be present. Windows may show a
SmartScreen warning because private builds are not code-signed.

## Privacy and networking

The default address, `ws://localhost:12345`, keeps the HapticScape-to-Intiface
connection on the same computer. HapticScape sends the Intiface handshake and
device commands required to play the selected feedback.

HapticScape does not send RuneScape account names, chat messages, passwords,
or information about other players to Intiface. RuneLite notification text is
not sent to Intiface; an accepted notification only triggers the configured
pattern.

If you configure a non-local server address, the operator of that server can
receive your IP address and haptic command traffic. Only connect to remote
servers you trust.

## Troubleshooting

### HapticScape stays on Connecting

- Confirm Intiface Central is open and its engine/server is running.
- Confirm the server address and port match Intiface.
- Use `ws://localhost:12345` when both applications run on the same computer.
- Disconnect and reconnect after restarting Intiface.

### The device does not appear

- Scan for the device inside Intiface Central first.
- Confirm the device works with Intiface's own test controls.
- Close manufacturer applications or other software that may already control
  the device.
- Check Intiface's documentation for the device's Bluetooth, USB, or serial
  requirements.

### The device appears but does not move

- Confirm Intiface reports vibration/scalar support for the device.
- Increase intensity; some devices ignore low values.
- Try **Stop now**, reconnect, and send another test pattern.
- Test a built-in Single pulse before troubleshooting a custom curve.

### The Windows launcher cannot find Java

Install the official RuneLite launcher first. The HapticScape launcher looks
for RuneLite's bundled Java runtime, then checks `JAVA_HOME` and `PATH`.

## Releases and updates

Private Windows bundles do not update themselves. Download or build a newer
bundle after a HapticScape update, and rebuild after a RuneLite or Old School
RuneScape update if compatibility changes.

## License and acknowledgements

HapticScape is distributed under the license in [LICENSE](LICENSE). Private
client bundles also include RuneLite's license in their `app\licenses`
directory.

RuneLite is a third-party client for Old School RuneScape. Intiface Central is
an independent open-source project built on the Buttplug framework. All names
and trademarks belong to their respective owners.

Bug reports and feature requests are welcome through
[GitHub Issues](https://github.com/ashy0019/HapticScape/issues).
