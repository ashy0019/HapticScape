# LumBridge

LumBridge is the fallback delivery path for HapticScape's tiny RuneLite
Local Event Bridge. It packages normal RuneLite plus the bridge as a built-in
plugin. The standalone HapticScape application remains in the root project and
has no RuneLite dependency.

The bridge source is vendored from:

- https://github.com/ashy0019/runelite-local-event-bridge

`BRIDGE-SOURCE.properties` records the exact commit for the vendored snapshot. Use
`sync-runelite-bridge.ps1` from the repository root whenever the canonical
bridge repository changes.

## Development

From the HapticScape repository root:

```powershell
.\gradlew.bat :runelite-bridge-client:test
.\gradlew.bat :runelite-bridge-client:runBridgeClient
```

The development launcher adds RuneLite's `--developer-mode --debug` flags. The
packaged launcher starts RuneLite normally.

## Build the packaged client JAR

```powershell
.\gradlew.bat :runelite-bridge-client:verifyBridgeClientArtifact
```

The resulting LumBridge JAR is:

```text
runelite-bridge-client\build\libs\lumbridge.jar
```

The build verifies that the launcher, bridge plugin, and provenance metadata
are present and that test classes or the standalone HapticScape application do
not leak into the RuneLite client artifact.

## Windows distribution

```powershell
.\package-bridge-client.ps1 -Version X.Y.Z
```

This produces a Windows ZIP and SHA-256 checksum under `build\distribution`.
The small native launcher reuses RuneLite's installed JRE when available, then
falls back to `JAVA_HOME` or Java on `PATH`.
