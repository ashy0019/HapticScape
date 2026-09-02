# HapticScape private build

This package is an unofficial RuneLite build containing the HapticScape plugin.
It is not endorsed by RuneLite or Jagex.

## Before launching

1. Install the official RuneLite launcher. HapticScape uses RuneLite's bundled
   Java runtime but does not modify the official installation.
2. Install and start Intiface separately. HapticScape connects to
   `ws://localhost:12345` by default.
3. If you use a Jagex Account, generate your own local RuneLite credentials by
   following RuneLite's official development guide:
   <https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>
4. Never send anyone your `.runelite/credentials.properties` file. It contains
   login credentials for your own account.
5. Run `HapticScape.exe`, open the HapticScape side panel, and connect to
   Intiface.

Windows may display a SmartScreen warning because this private build is not
code-signed. Only run software obtained directly from a person you trust.

## Updating

Version 1.6.0 is the first package with built-in updates and must be installed
manually. After that, the launcher can offer stable releases from the official
HapticScape GitHub repository before RuneLite starts.

Open **Updates** with the HapticScape panel's bottom controls to configure these
choices independently:

- **Install updates automatically**
- **Notify me when updates are available**

With both disabled, startup does not contact GitHub. **Check now** performs a
manual check and, when a newer release exists, schedules it for the next
HapticScape launch. Selecting **Skip this version** ignores only that release;
a later version can still be offered.

Do not move individual files out of the HapticScape folder. The updater needs
the complete writable folder and cannot update an installation stored in a
location that requires administrator permission.
