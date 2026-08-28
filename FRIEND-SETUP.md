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

This package does not update itself. Download a newly built package after a
RuneLite or Old School RuneScape update if the client stops working.
