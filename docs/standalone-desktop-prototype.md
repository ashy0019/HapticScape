# Standalone desktop prototype

Phase 3D.4 adds a standalone desktop entry point without changing the existing RuneLite client package yet.

Run it from the project root with:

```text
.\\gradlew.bat runStandalone
```

The standalone host owns the neutral `HapticScapeRuntime`, file-backed settings, desktop services, the localhost gameplay server, and the reusable Swing UI.

For this phase, do not run RuneLite-hosted HapticScape at the same time: both hosts still try to own the default gameplay port `127.0.0.1:41713`. The next process-split phase makes RuneLite client-only so it can connect to this standalone runtime instead.

The existing Windows package and `HapticScapeClient` main class remain unchanged in this phase.
