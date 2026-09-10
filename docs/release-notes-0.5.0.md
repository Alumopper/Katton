# Katton Alpha 0.5.0 — Release Notes

Katton Alpha 0.5.0 brings client scenes and camera effects, isolated script-pack
dependencies with transactional reload, audio playback, and a local IDE
development bridge. These notes consolidate all 0.5 updates, including build1,
build2, and the changes since build2, relative to Alpha 0.4.0. It supports
Minecraft 26.1.2 and 26.2 on Fabric, NeoForge, and Paper, and requires Java 25.

## Added

### Client scenes, camera, and world effects

- Added camera paths, target follow, look-at, camera shake, and FOV transitions.
- Added linear and Catmull–Rom position interpolation, shortest-path quaternion
  rotation interpolation, and easing functions.
- Added point, line, ring, sphere, and spiral particle emitters, plus beams,
  trails, rings, textured planes, curves, faces, boxes, spheres, cylinders, and cones.
- Effects support alpha/additive blending, depth testing, and explicit
  through-wall rendering.
- Added a Kotlin scene timeline with sequential, parallel, delayed, paused,
  resumed, repeated, and cancelled tracks.
- Servers can start or stop registered scenes for one player or nearby players.
- Added client budgets for active effects, particles per tick, and geometry
  vertices per frame, plus the `examples/client-scenes` script pack.
- See the [client scenes guide](https://github.com/Alumopper/Katton/blob/v0.5.0/docs/client-scenes.md).

### Script-pack runtime and dependencies

- Packs can load from directories or ZIP files; ZIP packs place `manifest.json`
  at the archive root.
- Packs can bundle private `libs/*.jar` dependencies and declare direct Katton
  pack dependencies with explicit transitive exports.
- Compiled dependency output and Kotlin metadata are exposed through the
  declared dependency graph; each pack retains its private library isolation.
- Reloading a consumer preserves an unchanged dependency instance and its shared
  state. Reloading a dependency replaces affected consumers in the same transaction.
- Failed candidate activation restores managed callbacks and resource values
  without replaying old entrypoints. Independent dependency components commit
  separately, so an unrelated successful update survives another component's failure.
- Immutable pack snapshots unify compilation, content hashes, signatures, and
  synchronization. The final 0.5.0 content rules below extend build2's coverage
  to arbitrary pack files.
- Added `examples/pack-dependencies` and
  [pack layout and dependency documentation](https://github.com/Alumopper/Katton/blob/v0.5.0/docs/kattonpacks-manifest-and-layout.md).

### Audio

- Added `top.katton.api.audio`: play audio on the local Katton client from pack
  files, exact resources, or sound events, with pause, resume, seek, stop,
  playback rate, volume, loop, and timed fades.
- Decoding supports PCM WAV, MP3, Ogg Vorbis, and FLAC, mono or stereo at 8–192
  kHz, with FLAC at 8/16/24 bits. The file suffix is not used; content is sniffed.
- Added spatial playback: a position or a followed entity produces mono
  attenuation-limited audio, while no anchor produces stereo background audio.
- Added `playPlayerAudio` for script-driven playback on a remote modded client,
  with client-confirmed snapshots (`refresh()`).
- Added `playBasicSound` / `stopBasicSound`, which work on Fabric, NeoForge, and
  Paper/Folia and only send vanilla sound packets to the player's client.
- Added a bounded disk PCM cache (512 MiB per file, 1 GiB total) and a
  per-client cap of 64 concurrent instances.
- Pack audio files are ordinary pack content and travel through the existing
  signed pack synchronization; a single file is limited to 16 MiB.
- Added `examples/audio`, `examples/audio-eltaw`, and the
  [audio control guide](https://github.com/Alumopper/Katton/blob/v0.5.0/docs/audio-control.md).

### IDE development bridge

- Added an optional loopback HTTP bridge (`/katton dev enable|disable`, or the
  **IDE** button in the script pack screen) that lets the Katton IDEA plugin
  discover the running instance, deploy world script packs, reload them through
  the normal serialized reload path, and stream compiler diagnostics and log
  output back to the IDE.
- Binds `127.0.0.1` only, requires a per-session bearer token, and is disabled by
  default. A client attached to a remote server has no deployment authority.
- Added `docs/idea-development.md`.

### Build

- The root `runClient` / `runServer` tasks now launch the active Stonecutter
  Fabric target instead of Loom's root development launcher.
- Paperweight was updated to `2.0.0-beta.23`.
- The signing plugin moved to the separate
  [Katton-Sign](https://github.com/Alumopper/Katton-Sign) repository
  ([migration guide](https://github.com/Alumopper/Katton/blob/v0.5.0/docs/sign-plugin-migration.md)).

## Changed

- **Pack content is now everything in the pack except `manifest.json` and
  `.kattonpack.state.json`.** Previously only sources, `libs/*.jar`, `assets/**`,
  and `data/**` were pack content. Additional files (audio, README files,
  configuration data) are now hashed, signed, synchronized, and counted against
  the existing per-file, per-pack, and per-bundle limits. **Packs that contain
  such files must be re-signed with the current Katton-Sign version.**
- Because the content classifier changed, the script-pack wire protocol and the
  client cache generation were bumped: the packet magic is now `0x4b500004` and
  the client cache directory is `serverpacks-v4`. Mixed 0.5.0-build2 and 0.5.0
  peers now report an explicit protocol mismatch instead of a misleading
  incomplete-snapshot disconnect. Update every client and server together.
- `.kattonpack.state.json` is excluded by file name at any depth and in any
  casing, so the local enabled-state file can never be distributed. Excluded
  files no longer consume the per-pack file cap or the content byte budget, and
  a ZIP pack now charges its manifest against the same budget as an equivalent
  directory pack, so both transports accept the same logical packs.
- Local development deployment now requires a manifest with an explicit `id`;
  the deployment identity, the owners file, and the on-disk pack folder agree on
  one identifier.

## Fixed

- NeoForge registered the `katton:audio_v1` payload twice for the play phase,
  which aborted network registry setup and prevented the mod from starting on
  both supported Minecraft versions. It is now one bidirectional registration.
- The mono downmix for spatial audio stepped through interleaved stereo samples
  one frame at a time, so it wrote only the left channel and produced half the
  expected duration. Spatial playback now averages each left/right pair.
- The PCM cache charged removed entries against the 1 GiB budget without ever
  releasing them, which eventually failed every later decode until restart; its
  eviction path could also block a decoder thread on the cache monitor. Entries
  now return their bytes when removed, and eviction never blocks.
- Leaked file descriptors: an audio stream's `FileChannel` was never closed on
  stop, seek, loop change, or end of playback.
- OpenAL queue bookkeeping assumed every read produced a non-empty buffer, which
  permanently failed short clips.
- Client channel, lease, and stream state could be mutated from the client tick
  thread and from decoder threads. Every mutation now runs on Minecraft's sound
  executor, and the client tick performs one entity index instead of one linear
  scan per handle.
- Remote audio handles treated a respawn or dimension change as a disconnect
  (the `ServerPlayer` instance is replaced for the same UUID), which left client
  playback running with no way to stop it. Handles now re-bind to the current
  player instance and only close when the player actually leaves.
- One timed-out remote command permanently poisoned every later command and
  `refresh()`. A failed request no longer blocks the command chain, and a
  client-reported error now updates the snapshot.
- Closed and failed client audio instances are now reaped from both the local
  and the remote instance maps, so the 64-instance budget cannot be exhausted by
  instances that already ended.
- `seek()` and `setLoop()` after `ENDED` silently did nothing because playback
  was never re-armed.
- A malformed parked remote `START` packet could throw on the client tick thread
  and crash the client; the source is now validated once at admission and the
  deferred path is guarded.
- Remote audio on a NeoForge client without Katton now reports the documented
  error instead of surfacing an internal NeoForge exception.
- Remote audio handles are now released unconditionally when the server stops,
  not only after a successful global activation.
- The development reload path could hold both reload lanes forever if its
  preparation task was rejected or its activation never ran; the lanes are now
  released on submission failure and the abandonment watchdog gives up after a
  bounded wait.
- The Paper plugin now disables the IDE development bridge on disable/reload so
  it does not keep a stale port and session file.

## Validation

- Common tests pass on both Minecraft targets.
- Fabric, NeoForge, and Paper builds pass on both Minecraft targets, including
  `verifyReleaseArtifacts`, `verifyReleaseAgentArtifacts`,
  `verifyLocalMavenPublications`, and the startup-agent and injection gates.
- Audio regression coverage includes all four codecs, 24-bit PCM/FLAC, mono
  downmix sample accuracy, decode limits, OpenAL queue progress, handle
  controls, and immutable directory/ZIP/JAR pack content.
- See the [audio verification record](https://github.com/Alumopper/Katton/blob/v0.5.0/docs/audio-verification.md) and the
  [pack model verification record](https://github.com/Alumopper/Katton/blob/v0.5.0/docs/pack-model-paper-folia-live.md).

## Known limitations

- Pack isolation is not a security sandbox. Direct script side effects such as
  world edits, file writes, and static state are outside transactional rollback.
- Client camera, rendering, and audio playback require Katton on the client.
  Paper remains server-only and provides only `playBasicSound` / `stopBasicSound`.
- Scene playback does not provide frame-accurate synchronization between clients;
  players joining or entering the radius do not receive an already active scene.
- Third-party renderer and custom shader compatibility has not been verified.
- The Folia probe covers global callbacks. Entity-region and multi-region
  concurrency remain untested.
- Global pack code changes still require a game or server restart.
- Paper scripts can compile against `top.katton.client.audio.*` classes that
  cannot load on a server; those classes are documented as client-only, and the
  supported server entry points fail or return cleanly instead of loading them.

## Release artifacts

The release contains six deployment JARs and `SHA256SUMS`:

- Fabric for Minecraft 26.1.2 and 26.2
- NeoForge for Minecraft 26.1.2 and 26.2
- Paper for Minecraft 26.1.2 and 26.2

Maven artifacts are published to
[`maven-releases`](https://nexus.mcfpp.top/repository/maven-releases/)
under group `top.katton`, with artifact IDs `katton-common`, `katton-fabric`,
`katton-neoforge`, and `katton-paper`. Select version `0.5.0+mc26.1.2` or
`0.5.0+mc26.2` to match the target Minecraft version. Maven platform JARs are
lean development dependencies; install the deployment JARs attached to this
release in the game/server. The signing plugin is released separately from
Katton-Sign.

[Full changes since Alpha 0.4.0](https://github.com/Alumopper/Katton/compare/v0.4.0...v0.5.0).
