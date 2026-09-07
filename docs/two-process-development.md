# Two-process development mode

Phase 3D.5 can run RuneLite and HapticScape as two real JVM processes while the existing embedded runtime remains available as a transitional fallback.

## Start order

Open two terminals from the repository root.

Terminal 1 — standalone HapticScape runtime and desktop UI:

```powershell
.\gradlew.bat runStandalone
```

Wait for the HapticScape desktop window to open. It owns the gameplay TCP server on `127.0.0.1:41713`.

Terminal 2 — RuneLite with the client-only gameplay bridge mode:

```powershell
.\gradlew.bat runExternalBridge
```

In this mode the RuneLite plugin does not construct HapticScapeRuntime, the sidebar, the Level-99 overlay, Intiface, Remote Play, Discord, music capture, or any other HapticScape runtime service. It only observes RuneLite events, translates them to neutral HapticScape events, and sends them to the standalone process over the localhost transport.

The current `run` task still starts the embedded compatibility mode during this transitional phase. Phase 3D.6 will remove that embedded path once the split path has been smoke-tested.

## Expected failure mode

`runExternalBridge` currently expects the standalone process to be listening first. If HapticScape is not running, the bridge startup fails rather than silently falling back to the embedded runtime.

After both processes are running, the gameplay transport already supports reconnect and re-handshake if the established localhost connection is broken and the standalone server returns before the next successful send.

## Smoke test

With both processes running, verify one representative event from each family:

- XP gain
- phrase click
- HP or Prayer threshold
- inventory full
- valuable loot
- player death
- generic RuneLite notification
- hop/relog reset and reseed

The visible HapticScape UI should exist only in the standalone desktop process while using `runExternalBridge`.
