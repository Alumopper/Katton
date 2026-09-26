# Katton Alpha 0.5.1 — Release Notes

Katton Alpha 0.5.1 improves script-pack startup and hot reload and adds a
reload-safe Play networking API for Fabric and NeoForge scripts. It supports
Minecraft 26.1.2 and 26.2 on Fabric, NeoForge, and Paper and requires Java 25.

## Added

- Fabric and NeoForge scripts can exchange binary Play packets through Katton's
  process-wide `katton:script_payload` transport. A script registers a logical
  channel with `ScriptPlayNetworking` or `ClientScriptPlayNetworking`; Katton
  removes the generation's handlers on reload while keeping the packet codec
  stable. The payload limit is 16 KiB. See the
  [script networking guide](https://github.com/Alumopper/Katton/blob/v0.5.1/docs/script-play-networking.md).
- The IDE development bridge can opt in at launch with
  `-Dkatton.dev.autoEnable=true` or `KATTON_DEV_AUTO_ENABLE=true`. It remains
  disabled by default, and an integrated Fabric server closes it on stop.
- Compilation, classpath indexing, activation, and resource reload now emit
  stage timings to help distinguish Kotlin work from Minecraft thread waits.

## Improved and fixed

- World-pack compilation runs on a preparation worker, and initial client
  global-pack compilation is scheduled off the render thread. The server
  reload progress display now includes compilation, and the client reuses the
  prepared artifact for its lifecycle phases.
- Stable ordering of external host classpath entries makes disk compilation
  cache keys repeatable across launches. Manifest-only edits no longer trigger
  Kotlin recompilation; source and bundled-library changes still invalidate
  the artifact.
- Host JAR entries are indexed during preparation instead of repeatedly
  reopening JARs during script class lookup. Client REGISTRY_SETUP and JOINED
  execute in one render-thread dispatch, reducing avoidable queue waits.
- The server pauses its slow-login timeout while a connection waits for an
  in-progress script reload, so cold Kotlin compilation does not disconnect
  the joining client prematurely.
- Expected callback pauses during reload no longer appear as HUD or world
  renderer failures. Reload rollback and old-generation isolation remain in
  place.
- NeoForge damage handling now leaves damage allowed when no
  `onAllowDamage` listener is registered.

## Compatibility

- Scripts that register their own process-wide `CustomPacketPayload` type
  should migrate to the Katton logical-channel API before relying on hot
  reload. Restart the game/server once to clear old codec registrations.
- The new Play networking API requires Katton on both peers and is unavailable
  on Paper. It does not change the existing script-pack synchronization format.
- A first compilation may still take several seconds. The subsequent launch
  can use the persistent disk artifact when script sources and dependencies
  have not changed.

## Validation

- The full Gradle build, both common test suites, all six deployment-artifact
  checks, four deployment-JAR startup-agent checks, and local Maven
  publication verification passed for the release version.
- The final Fabric 26.2 deployment JAR loaded the `PR-Rhythm-Map` world and
  completed `/katton reload`. Its first Kotlin compile took 15.51 s; the
  next independent startup loaded the artifact from disk in 5 ms. Client
  JOINED activation took 443 ms including render-thread queue time, and
  the post-command client activation took 9 ms.

## Artifacts

The GitHub release contains six deployment JARs and `SHA256SUMS`: Fabric,
NeoForge, and Paper for both Minecraft 26.1.2 and 26.2. Maven development
artifacts are published under `top.katton` as `katton-common`,
`katton-fabric`, `katton-neoforge`, and `katton-paper`, each with
`0.5.1+mc26.1.2` and `0.5.1+mc26.2` versions. Install the GitHub deployment
JARs in the game/server; Maven platform JARs are lean compile dependencies.

[Changes since 0.5.0](https://github.com/Alumopper/Katton/compare/v0.5.0...v0.5.1)
